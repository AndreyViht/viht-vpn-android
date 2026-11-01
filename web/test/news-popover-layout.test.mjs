import test from 'node:test';
import assert from 'node:assert/strict';
import {newsPopoverLayout} from '../src/utils/newsPopoverLayout.mjs';

test('news panel stays below the header bell and inside narrow phones, tablets and landscape',()=>{
 for(const [width,height] of [[320,568],[360,800],[432,900],[800,360],[1024,768]]){
  const bell={left:width-54,right:width-18,bottom:45};
  const p=newsPopoverLayout(bell,{width,height});
  assert.equal(p.top,55);
  assert.ok(p.left>=12);
  assert.ok(p.left+p.width<=width-12);
  assert.ok(p.top+p.maxHeight<=height-12);
 }
});

test('news panel respects a shifted visual viewport when zoom or the keyboard changes available space',()=>{
 const view={width:320,height:240,left:45,top:100};
 const p=newsPopoverLayout({left:295,right:331,bottom:145},view);
 assert.ok(p.left>=view.left+12);
 assert.ok(p.left+p.width<=view.left+view.width-12);
 assert.ok(p.top>=view.top+12);
 assert.ok(p.top+p.maxHeight<=view.top+view.height-12);
});
