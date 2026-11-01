// Local maintainer tool. Credentials are read from the existing local helper, never uploaded.
const fs=require('fs'), path=require('path'), crypto=require('crypto');
const repo='AndreyViht/viht-vpn-android', branch='repair/android-1.4.0';
const source=fs.readFileSync('push_to_github.js','utf8');
const token=(source.match(/const TOKEN\s*=\s*['"]([^'"]+)['"]/)||source.match(/const GITHUB_TOKEN\s*=\s*['"]([^'"]+)['"]/))?.[1];
if(!token) throw Error('Missing local GitHub credential');
async function api(route,method='GET',body){
 const r=await fetch('https://api.github.com/repos/'+repo+route,{method,headers:{Authorization:'Bearer '+token,'User-Agent':'Viht-Android-Repair',Accept:'application/vnd.github+json'},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(60000)});
 if(!r.ok) throw Error(method+' '+route+': HTTP '+r.status+' '+(await r.text()).slice(0,300));
 return r.status===204?null:r.json();
}
async function upload(){
 const base=JSON.parse(fs.readFileSync('tools/repair-baseline.json'));
 let ref;try{ref=await api('/git/ref/heads/'+branch)}catch(e){if(!String(e).includes('404'))throw e;}
 const parent=ref?.object.sha||(await api('/git/ref/heads/main')).object.sha;
 if(!ref&&parent!==base.baseCommit) throw Error('Main moved: review remote changes first');
 const commit=await api('/git/commits/'+parent), tree=await api('/git/trees/'+commit.tree.sha+'?recursive=1');
 if(tree.truncated)throw Error('Truncated remote tree');
 const old=new Map(tree.tree.filter(x=>x.type==='blob').map(x=>[x.path,x]));
 const skip=/^(?:\.git|\.gradle|\.idea|build|release-apk|release-apks|tmp-libbox|captures|web\/node_modules|app\/build)(?:\/|$)|^(?:push_to_github\.js|local\.properties|tools\/repair-baseline\.json|tools\/ci-repair\.cjs)$|\.(?:jks|keystore|apk|log)$/;
 const files=new Map();function walk(dir=''){for(const entry of fs.readdirSync(dir||'.',{withFileTypes:true})){const name=(dir?dir+'/':'')+entry.name;if(skip.test(name)||name==='app/libs/libbox.aar')continue;if(entry.isDirectory())walk(name);else if(entry.isFile())files.set(name,fs.readFileSync(name));}}walk();
 const changes=[];
 for(const [name,data] of files){const hash=crypto.createHash('sha1').update('blob '+data.length+'\0').update(data).digest('hex');if(hash===old.get(name)?.sha)continue;const blob=await api('/git/blobs','POST',{content:data.toString('base64'),encoding:'base64'});changes.push({path:name,mode:old.get(name)?.mode||'100644',type:'blob',sha:blob.sha});}
 for(const [name,entry]of old){if((name.startsWith('app/src/main/assets/web/')&&!files.has(name))||name.endsWith('/VihtApiClient.kt')||name==='web/src/components/SettingsModal.jsx')changes.push({path:name,mode:entry.mode,type:'blob',sha:null});}
 if(changes.length){const nextTree=await api('/git/trees','POST',{base_tree:commit.tree.sha,tree:changes});const next=await api('/git/commits','POST',{message:'Repair Android session, VPN lifecycle and shared Liquid Glass UI',tree:nextTree.sha,parents:[parent]});if(ref)await api('/git/refs/heads/'+branch,'PATCH',{sha:next.sha,force:false});else await api('/git/refs','POST',{ref:'refs/heads/'+branch,sha:next.sha});console.log(JSON.stringify({commit:next.sha,changed:changes.length,branch}));}else console.log('No source changes');
 await api('/actions/workflows/android-release.yml/dispatches','POST',{ref:branch});console.log('Test workflow dispatched');
}
async function main(){const cmd=process.argv[2];if(cmd==='upload')return upload();if(cmd==='cancel')return api('/actions/runs/'+process.argv[3]+'/cancel','POST');if(cmd==='artifact'){
 const data=await api('/actions/runs/'+process.argv[3]+'/artifacts');
 const item=data.artifacts.find(x=>x.name===(process.argv[4]||'VihtVPN-Android-test'));if(!item)throw Error('Artifact unavailable');
 const r=await fetch('https://api.github.com/repos/'+repo+'/actions/artifacts/'+item.id+'/zip',{headers:{Authorization:'Bearer '+token,'User-Agent':'Viht-Android-Repair'},signal:AbortSignal.timeout(180000)});
 if(!r.ok)throw Error('Artifact HTTP '+r.status);fs.mkdirSync('release-apks',{recursive:true});const target='release-apks/'+item.name+'.zip';fs.writeFileSync(target,Buffer.from(await r.arrayBuffer()));console.log(JSON.stringify({path:target,size:fs.statSync(target).size,id:item.id}));return;
 }if(cmd==='runs'){const data=await api('/actions/runs?branch='+encodeURIComponent(branch)+'&per_page=3');console.log(JSON.stringify(data.workflow_runs.map(x=>({id:x.id,status:x.status,conclusion:x.conclusion,url:x.html_url,sha:x.head_sha})),null,2));return;}if(cmd==='jobs'){const data=await api('/actions/runs/'+process.argv[3]+'/jobs');console.log(JSON.stringify(data.jobs.map(x=>({id:x.id,status:x.status,conclusion:x.conclusion,steps:x.steps.filter(s=>s.status!=="pending").map(s=>({name:s.name,status:s.status,conclusion:s.conclusion}))})),null,2));return;}if(cmd==='logs'){const id=process.argv[3];const r=await fetch('https://api.github.com/repos/'+repo+'/actions/jobs/'+id+'/logs',{headers:{Authorization:'Bearer '+token,'User-Agent':'Viht-Android-Repair'},signal:AbortSignal.timeout(60000)});if(!r.ok)throw Error('Logs HTTP '+r.status);const log=await r.text();fs.mkdirSync('release-apks',{recursive:true});fs.writeFileSync('release-apks/ci.log',log);console.log(log.split('\n').filter(x=>/error|failed|^e:|Exception|What went wrong|FAILURE|\.kt:\d/i.test(x)).slice(-100).join('\n'));return;}throw Error('Unknown command');}
main().catch(e=>{console.error(e.message);process.exitCode=1;});
