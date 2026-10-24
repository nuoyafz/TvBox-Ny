#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
随机看改动冒烟：布局 id 与 Java 引用一致性 + 模式分支完整性。
不需要设备，纯静态校验（防 R.id 缺失导致的 NPE / 模式漏分支）。
"""
import io
import re
import sys

ROOT = 'app/src/main/'
LAYOUT = ROOT + 'res/layout/dialog_random_pick.xml'
JAVA = ROOT + 'java/com/github/tvbox/osc/ui/dialog/RandomPickDialog.java'

fails = []


def check(name, ok, extra=''):
    print(('  ok   ' if ok else '  FAIL ') + name + (('  ' + extra) if extra else ''))
    if not ok:
        fails.append(name)


layout = io.open(LAYOUT, encoding='utf-8').read()
java = io.open(JAVA, encoding='utf-8').read()

ids = set(re.findall(r'@\+id/(\w+)', layout))
print('布局 id 数: %d' % len(ids))

# 1) Java 里引用的 R.id.xxx 必须都在布局里定义
refs = set(re.findall(r'R\.id\.(\w+)', java))
missing = sorted(r for r in refs if r not in ids)
check('Java 引用的 R.id 全部存在于布局', not missing, '缺失=' + str(missing))
check('新增按钮 btnPickAll 已定义且被引用', 'btnPickAll' in ids and 'btnPickAll' in refs)

# 2) 四个模式都要有分支
for pat, label in [
    (r'mode == 3', '全部模式(mode 3) 有分支'),
    (r'case 3: return allCategories;', 'currentPool 覆盖全部池'),
    (r'btnAll', '全部按钮已接线'),
]:
    check(label, re.search(pat, java) is not None)

# 3) 分页随机必须真的用上，且不能再写死第 1 页
check('随机页已接入', 'RandomPickRules.randomPage' in java)
check('没有写死 getList(request, 1)', 'getList(request, 1)' not in java)
check('深页回退到第 1 页', 'if (page > 1)' in java)

# 4) 观察者必须在所有出口 detach（防泄漏）: 出现次数 >= 3
check('detachCategoryObserver 覆盖各出口', java.count('detachCategoryObserver()') >= 4,
      '次数=%d' % java.count('detachCategoryObserver()'))

print('----')
print('静态校验失败项: %d' % len(fails))
sys.exit(1 if fails else 0)
