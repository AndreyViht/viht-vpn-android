export const journalFilters = [
  ['all','Все'], ['vpn','VPN'], ['dns','DNS'], ['traffic','Маршруты'],
  ['api','Сервис'], ['errors','Ошибки'], ['system','Система'],
];

const patterns = {
  vpn: /\bvpn\b|starting vpn|reconnect|туннел|подключ/i,
  dns: /\bdns\b|app\/dns|doh|resolver/i,
  traffic: /rout(e|ing|er)|accepted|inbound|outbound|forward|tcp:|udp:/i,
  api: /API action=|HTTP [1-5]\d\d/i,
  errors: /\b(error|failed|failure|warning|timeout|exception)\b|ошибк|не удалось|status=[45]\d\d/i,
};

export function filterJournal(content, category) {
  if (category === 'all') return content;
  return content.split(/\r?\n/).filter(line => {
    if (!line.trim()) return false;
    if (category === 'system') return !Object.values(patterns).some(pattern => pattern.test(line));
    return patterns[category]?.test(line);
  }).join('\n');
}
