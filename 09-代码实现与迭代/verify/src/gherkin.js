'use strict';
/**
 * 极简 Gherkin 解析器（够用即可）
 * 支持：# 注释 / 功能: / 场景: / 假如|当|那么|并且|但是
 * 关键字的英文别名与中文关键字均支持。
 */
const KW = ['假如', '当', '那么', '并且', '但是', '而且', '且'];
const ALIAS = {
  'Given': '假如', 'When': '当', 'Then': '那么', 'And': '并且', 'But': '但是',
};

function parse(text) {
  const features = [];
  let current = null;
  let scenario = null;

  for (let raw of text.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;

    // 去掉行首的 Gherkin 关键字别名（如 "Given xxx"）
    let content = line;
    for (const [en, cn] of Object.entries(ALIAS)) {
      if (content.startsWith(en + ' ')) { content = cn + ' ' + content.slice(en.length + 1); break; }
    }

    if (content.startsWith('功能:') || content.startsWith('Feature:')) {
      current = { name: content.replace(/^(功能:|Feature:)/, '').trim(), scenarios: [] };
      features.push(current);
      scenario = null;
      continue;
    }
    if (content.startsWith('场景:') || content.startsWith('Scenario:')) {
      scenario = { name: content.replace(/^(场景:|Scenario:)/, '').trim(), steps: [] };
      if (!current) { current = { name: '(未命名功能)', scenarios: [] }; features.push(current); }
      current.scenarios.push(scenario);
      continue;
    }
    const kw = KW.find(k => content.startsWith(k));
    if (kw && scenario) {
      scenario.steps.push({ keyword: kw, text: content.slice(kw.length).trim() });
      continue;
    }
    // 其他行（如语言声明、表格）忽略
  }
  return features;
}

module.exports = { parse };
