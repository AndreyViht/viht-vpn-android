const groups = [
  ["home", "Главная", "Home", [
    ["connect-shape","Форма кнопки VPN","VPN button shape",["Скруглённая","Rounded square"],["Плитка","Tile"]],
    ["connect-size","Размер кнопки VPN","VPN button size",["Меньше","Smaller"],["Больше","Larger"]],
    ["connect-fill","Заливка кнопки VPN","VPN button fill",["Матовая","Matte"],["В цвет темы","Theme color"]],
    ["connect-glow","Свечение кнопки VPN","VPN button glow",["Тише","Subtle"],["Ярче","Bright"]],
    ["renew-style","Кнопка продления","Renew button",["Контур","Outline"],["Сплошная","Solid"]]
  ]],
  ["servers", "Серверы", "Servers", [
    ["server-density","Высота карточек","Card height",["Компактные","Compact"],["Просторные","Spacious"]],
    ["server-shape","Форма карточек","Card shape",["Строгая","Square"],["Капсула","Pill"]],
    ["server-surface","Поверхность карточек","Card surface",["Подложка","Tinted"],["Контур","Outline"]],
    ["server-selection","Выбранный сервер","Selected server",["Полоса","Side stripe"],["Контур","Outline"]],
    ["server-action","Кнопки замера и синхронизации","Ping and sync buttons",["Контур","Outline"],["Подложка","Tinted"]],
    ["server-flag","Флаги","Flags",["Круглые","Round"],["Крупные","Large"]],
    ["ping-display","График пинга","Ping chart",["Только число","Number only"],["Выше столбцы","Taller bars"]]
  ]],
  ["type", "Текст", "Text", [
    ["heading-size","Размер заголовков","Heading size",["Меньше","Smaller"],["Больше","Larger"]],
    ["heading-weight","Начертание заголовков","Heading weight",["Среднее","Medium"],["Жирное","Bold"]],
    ["body-size","Размер подписей","Label size",["Крупнее","Larger"],["Очень крупно","Extra large"]],
    ["secondary-contrast","Контраст подписей","Label contrast",["Выше","High"],["Максимальный","Highest"]],
    ["number-style","Цифры пинга и таймера","Ping and timer numerals",["Моноширинные","Monospace"],["Жирные","Bold"]]
  ]],
  ["navigation", "Навигация и настройки", "Navigation and settings", [
    ["nav-shape","Форма панели навигации","Navigation bar shape",["Прямее","Square"],["Капсула","Pill"]],
    ["nav-surface","Поверхность навигации","Navigation bar surface",["Матовая","Matte"],["В цвет темы","Theme color"]],
    ["nav-selection","Активная вкладка","Active tab",["Точка","Dot"],["Подчёркивание","Underline"]],
    ["nav-label","Подписи вкладок","Tab labels",["Крупнее","Larger"],["Только значки","Icons only"]],
    ["nav-icon","Размер значков","Navigation icon size",["Меньше","Smaller"],["Больше","Larger"]],
    ["settings-density","Высота строк настроек","Settings row height",["Плотнее","Compact"],["Свободнее","Spacious"]],
    ["settings-style","Вид строк настроек","Settings row style",["Плитки","Tiles"],["Разделители","Dividers"]]
  ]],
  ["surface", "Фон и эффекты", "Background and effects", [
    ["background","Фон приложения","App background",["Матовый","Matte"],["Градиент","Gradient"]],
    ["effects","Декоративные эффекты","Decorative effects",["Спокойно","Calm"],["Без свечения","No glow"]]
  ]]
];

export default groups;

const optionsByKey = new Map(groups.flatMap(([, , , options]) =>
  options.map(([key, , , ...values]) => [key, values.length])));

export function designAttributes(design) {
  return Object.fromEntries(Object.entries(design || {}).flatMap(([key, value]) =>
    Number.isInteger(value) && value > 0 && value <= (optionsByKey.get(key) || 0)
      ? [['data-design-' + key, value]] : []));
}
