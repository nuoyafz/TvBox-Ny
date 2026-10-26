#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
搜索页改动冒烟（无设备，纯静态）：
1. 布局 id 与 Java R.id 引用一一对应（防 NPE / 找不到控件）
2. 键盘改 3 行后：28 个键、10 列、功能键 span=2
3. 首字母联想：TextWatcher + 防抖存在，旧的位置判断已移除
4. 词栏切换不再靠文案反推
"""
import io
import re
import sys

LAYOUT = 'app/src/main/res/layout/activity_search.xml'
JAVA = 'app/src/main/java/com/github/tvbox/osc/ui/activity/SearchActivity.java'
KB = 'app/src/main/java/com/github/tvbox/osc/ui/tv/widget/SearchKeyboard.java'

fails = []


def check(name, ok, extra=''):
    print(('  ok   ' if ok else '  FAIL ') + name + (('  ' + extra) if extra else ''))
    if not ok:
        fails.append(name)


layout = io.open(LAYOUT, encoding='utf-8').read()
java = io.open(JAVA, encoding='utf-8').read()
kb = io.open(KB, encoding='utf-8').read()

ids = set(re.findall(r'@\+id/(\w+)', layout))
refs = set(re.findall(r'R\.id\.(\w+)', java))
missing = sorted(r for r in refs if r not in ids)
check('SearchActivity 引用的 id 全部存在', not missing, '缺失=' + str(missing))

# 关键控件必须还在（LoadSir / 词栏 / 键盘 / 结果）
for need in ['etSearch', 'tvSearch', 'tvClear', 'tvSearchCheckboxBtn', 'wordSwitch',
             'keyBoardRoot', 'mGridView', 'mGridViewWord', 'llLayout', 'llWord',
             'llHistoryWord', 'historyWordGrid', 'remoteRoot']:
    check('布局保留 ' + need, need in ids)

# 上下分区：根是 vertical，llLayout 用 weight 吃剩余高度
check('根布局改为垂直', re.search(r'<LinearLayout[^>]*android:orientation="vertical"', layout) is not None)
check('结果区使用 layout_weight', 'android:layout_weight="1"' in layout)

# 键盘 3 行
letters = re.findall(r'"([A-Z])"', kb)
check('键盘 26 个字母', len(letters) == 26, '实际=%d' % len(letters))
check('键盘 10 列', 'GridLayoutManager(getContext(), 10)' in kb)
check('功能键各占 2 格', 'position >= 26 ? 2 : 1' in kb)
check('键盘无数字键', not re.search(r'keys = .*"1"', kb))
check('删除/远程搜索仍在', '"删除"' in kb and '"远程搜索"' in kb)

# 首字母联想
check('输入监听已接入', 'addTextChangedListener' in java)
check('联想有防抖', 'postDelayed(recPending, 350)' in java)
check('按键回调改为按 key 判断', '"删除".equals(key)' in java and 'pos == 1' not in java)
check('词栏状态位替代文案反推', 'showingHistoryWords' in java)

print('----')
print('静态校验失败项: %d' % len(fails))
sys.exit(1 if fails else 0)
