#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
搜索页 C5 左右分栏冒烟（无设备，纯静态）：

1. 布局 id 与 Java R.id 引用一一对应（防 NPE / 找不到控件）
2. 左右分栏结构：根 horizontal、双栏 weight、竖分隔线
3. 键盘 3 行 10 列：26 字母 + 删除/远程搜索各 span 2 = 30 格
4. 「打字即预览」链路真的接通了：loadRec onSuccess -> showPreview -> previewGrid VISIBLE
   （这一条是回归红线：之前就是这里断的，打字只查词条、右侧永远空）
5. 历史搜索独立常驻，不再跟结果区互斥
"""
import io
import re
import sys

LAYOUT = 'app/src/main/res/layout/activity_search.xml'
JAVA = 'app/src/main/java/com/github/tvbox/osc/ui/activity/SearchActivity.java'
KB = 'app/src/main/java/com/github/tvbox/osc/ui/tv/widget/SearchKeyboard.java'
PREVIEW_ITEM = 'app/src/main/res/layout/item_search_preview.xml'
PREVIEW_ADAPTER = 'app/src/main/java/com/github/tvbox/osc/ui/adapter/PreviewWordAdapter.java'

fails = []


def check(name, ok, extra=''):
    print(('  ok   ' if ok else '  FAIL ') + name + (('  ' + extra) if extra else ''))
    if not ok:
        fails.append(name)


layout = io.open(LAYOUT, encoding='utf-8').read()
java = io.open(JAVA, encoding='utf-8').read()
kb = io.open(KB, encoding='utf-8').read()
pitem = io.open(PREVIEW_ITEM, encoding='utf-8').read()
padapter = io.open(PREVIEW_ADAPTER, encoding='utf-8').read()

print('[1] id 完整性')
ids = set(re.findall(r'@\+id/(\w+)', layout))
refs = set(re.findall(r'R\.id\.(\w+)', java))
missing = sorted(r for r in refs if r not in ids)
check('SearchActivity 引用的 id 全部存在', not missing, '缺失=' + str(missing))

for need in ['etSearch', 'tvSearch', 'tvClear', 'tvSearchCheckboxBtn', 'wordSwitch',
             'keyBoardRoot', 'mGridView', 'mGridViewWord', 'llLayout', 'llWord',
             'llHistoryWord', 'historyWordGrid', 'remoteRoot']:
    check('旧控件保留 ' + need, need in ids)

for need in ['previewGrid', 'tvResultTitle', 'tvResultCount', 'tvResultEmpty']:
    check('新增控件 ' + need, need in ids)

print('[2] 左右分栏结构')
check('根布局改为水平', re.search(r'<LinearLayout[^>]*android:orientation="horizontal"', layout) is not None)
check('左栏 weight=45', 'android:layout_weight="45"' in layout)
check('右栏 weight=55', 'android:layout_weight="55"' in layout)
check('有竖分隔线', '@color/neon_divider' in layout)
check('结果区包在 FrameLayout 里叠三层', layout.count('previewGrid') == 1 and layout.count('tvResultEmpty') == 1)

print('[3] 键盘')
letters = re.findall(r'"([A-Z])"', kb)
check('26 个字母', len(letters) == 26, '实际=%d' % len(letters))
check('10 列', 'GridLayoutManager(getContext(), 10)' in kb)
check('功能键各占 2 格', 'position >= 26 ? 2 : 1' in kb)
check('无数字键', not re.search(r'keys = .*"1"', kb))
check('删除/远程搜索仍在', '"删除"' in kb and '"远程搜索"' in kb)
check('键盘 30 格正好 3 行', len(letters) + 2 + 2 == 30)

print('[4] 打字即预览（回归红线）')
check('输入监听存在', 'addTextChangedListener' in java)
check('联想防抖 350ms', 'postDelayed(recPending, 350)' in java)
check('loadRec 回调里调了 showPreview',
      re.search(r'setHotWordsData\(hots\);[\s\S]{0,400}?showPreview\(hots, key\)', java) is not None)
check('showPreview 里 previewGrid 置为 VISIBLE',
      re.search(r'private void showPreview[\s\S]{0,1500}?previewGrid\.setVisibility\(View\.VISIBLE\)', java) is not None)
check('showPreview 里给标题写了「首字母 X 的预览」', '"首字母 " + key.toUpperCase() + " 的预览"' in java)
check('输入清空回空态', re.search(r'if \(key\.isEmpty\(\)\) \{[\s\S]{0,200}?showPreviewEmpty\(""\)', java) is not None)
check('搜索回填不触发预览', 'key.equals(searchTitle)' in java)
check('真搜索时预览让位', re.search(r'private void search\(String title\)[\s\S]{0,1200}?previewGrid\.setVisibility\(View\.GONE\)', java) is not None)
check('预览态作废旧搜索 token', 'currentSearchToken = String.valueOf(searchTokenSeq.incrementAndGet());' in java)

print('[5] 历史搜索独立常驻')
check('applySearchWordMode 不再把 llLayout 藏掉',
      'llLayout.setVisibility(View.GONE)' not in java)
check('历史块始终 VISIBLE', 'llHistoryWord.setVisibility(View.VISIBLE)' in java)
check('历史最多 6 条', 'Math.min(history.size(), 6)' in java)
check('词栏改 2 列', 'mGridViewWord.setLayoutManager(new V7GridLayoutManager(this.mContext, 2))' in java)
check('词栏不再靠按钮切换', 'setClickable(false)' in java)

print('[6] 预览卡片')
for need in ['tvPreviewName', 'tvPreviewIni']:
    check('预览卡片有 ' + need, need in pitem)
check('adapter 引用的 id 都存在',
      all(r in pitem for r in re.findall(r'R\.id\.(\w+)', padapter)))
check('adapter 有 setKeyword', 'public void setKeyword' in padapter)
check('adapter 用对布局', 'R.layout.item_search_preview' in padapter)

print('----')
print('静态校验失败项: %d' % len(fails))
sys.exit(1 if fails else 0)
