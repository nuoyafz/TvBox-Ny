function search() {
    doAction('search', { word: $('#search_key_word').val() });
}

function api() {
    doAction('api', { url: $('#diy_api_url').val() });
}

function liveApi() {
    doAction('liveApi', { url: $('#diy_live_api_url').val() });
}

function danmuApi() {
    doAction('danmuApi', { url: $('#diy_danmu_api_url').val() });
}

function push() {
    doAction('push', { url: $('#push_url').val() });
}

function doAction(action, kv) {
    kv['do'] = action;
    // POST 失败自动降级 GET：个别环境/代理会拦 POST，不能让按键无声无息丢掉
    $.post('/action', kv, function (data) {
        console.log(data);
    }).fail(function () {
        $.get('/action', kv);
    });
    return false;
}

function tpl_top(path) {
    return `<a class="weui-cell  weui-cell_access" href="javascript:void(0)" onclick="listFile('` + path + `')">
    <div class="weui-cell__hd"><img src="`+ ic_dir + `" alt="" style="width: 32px; margin-right: 16px; display: block;"></div>
    <span class="weui-cell__bd">
        <span>..</span>
    </span>
    <span class="weui-cell__ft">
    </span>
    </a>`;
}

function tpl_dir(name, time, path) {
    return `<a class="weui-cell  weui-cell_access" href="#" onclick="listFile('` + path + `')">
    <div class="weui-cell__hd"><img src="`+ ic_dir + `" alt="" style="width: 32px; margin-right: 16px; display: block;"></div>
    <span class="weui-cell__bd">
    <span>`+ name + `</span>
        <div class="weui-cell__desc">`+ time + `</div>
    </span>
    <span class="weui-cell__ft">
    </span>
    </a>`;
}

function tpl_file(name, time, path, canDel) {
    return `<a class="weui-cell  weui-cell_access" href="javascript:void(0)" onclick="selectFile('` + path + `', ` + canDel + `)">
    <div class="weui-cell__hd"><img src="`+ ic_file + `" alt="" style="width: 32px; margin-right: 16px; display: block;"></div>
    <span class="weui-cell__bd">
        <span>`+ name + `</span>
        <div class="weui-cell__desc">`+ time + `</div>
    </span>
    </a>`;
}

function clear_list() {
    $('#file_list').html('');
}

function add_file(node) {
    $('#file_list').append(node);
}

let ic_dir = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAAABHNCSVQICAgIfAhkiAAAAAlwSFlzAAAA7AAAAOwBeShxvQAAABl0RVh0U29mdHdhcmUAd3d3Lmlua3NjYXBlLm9yZ5vuPBoAAAIoSURBVFiF7Ze/T9RgGMc/T9/WO+74IXdRNKCJxETPQaOJi8LoxiouTurmv+DAYvwH3JzVhOhgSHByMTqY6GBCCBFMcJFwURC4O1rbvo8DoIg9rvHuYOGTdGjfb/J88vZpn1Z0bKw7JjsiaCcJqMiCKQ1OyuhonLTeLG5MZlLQq/UCooqd/nwfuNcOAUfgSorcbR0fN20RACRF7hgzc0PtEJB47IGmCSq8EXjbOKjG+VG54Buv531puK/WkfO2L0cY+ZI5ksVfD84vTt91U5vCEBvH7qxU0GqV18PX+Xg2+e6GFmZXhPlc3zOX5dW0Do2xMbIeEBuXmVMX68Y8BwxQzhXFlWqtdQKbzA+cIch0pMq630vgX55DTJgY+Bn18ql8hyAqphb4VjjeMLPVeG722nOKztddw0EPTARPUws0ohqB3TRw8w2KAwyaCRara/i2u+niCqxHf85TPQWe+Jw2L3kX3GhaYCdip5xU74FV28dSPNB8QZR8VEbCiCe1h+kFWopCVLH42oWz58Xh98s/y9o+CWzjQOBA4EBg3wX+mgWxdwlrTtSJRrjBK0T99glUC49R52jdcG75Fp7/on0C4BGGIeWFMtsHRL4zT2/hMCoerebfHkgYTWm+2/+XHTsQ4h3y6D/ZnxgWjRKvt0wgv3QTa+rN/I0mbDVipxxLe3c5kWjNgqIOSOO/nRajMVu99kF0hi4iM4LQtSfVrWbigHMa21k35NEvWSq4Cnb1Ay8AAAAASUVORK5CYII=';
let ic_file = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAAABHNCSVQICAgIfAhkiAAAAAlwSFlzAAAA7AAAAOwBeShxvQAAABl0RVh0U29mdHdhcmUAd3d3Lmlua3NjYXBlLm9yZ5vuPBoAAAJdSURBVFiF7ZfLaxNRFMa/c+8MbZO0xAgmGIIxUmtsQCkobsSNO12I7v0PXAjuBDeCCN0I/gvdu3HhQlHBBwouTA1UUoulKXmYSDp59TEzx4XUpmFm0tsMTRd+u5l7uN9vvnvunRmCh+r1+km2+CWAlFedm5byP79PIHRx8tKk4VYjvCawTfvafs0BoN1qThWNYn7h3cK4EgDPI23P07Ox5pM7+zXfVqPRPFbulPO517nQngBmv3JwkwIPQXQDXDs9KAAANI1WtLpVXcx9yEU8AWY/WU95nRvL1tVbfhgLKXcgGq1oZe3XajabPdJdo3VfMOg2AAL7YQ8kUyewIiQs0wQAbG5sjHbqnUUARx0BAMi/IP4oEAxganpnFcuFEggU7q7x3AUHoaED9C6Bp5gZlmUpGQgSENL9OR0B2KUJCssFFAslJQAShPMXzkHX9b0DuCmRTCCRTCgB9JPzSeirhbeUEjDqBgyjoWQgiBCLxyCEcx8494DLZLZtwzbVmpAFeUaqlEA4EkY4Eu5fqKBDeg64bsNVVEoVJQMCITOTga47WznfJefJ4onjiMWjygBSk67jSgcRCYImlNqmr4beA0M/iP4ncDgT+BfBAUThmQDbpv+OPZ+8ngBa+xWEWfbFt9PqgJmh6/r7XR5OxduIkpcw9uMsWE4MZM7QMKoFq2uBF2dS6VO1vgArWzPIjDwHSUCOA2DXf0sFit9z6XS61nu7F8AEgLfrD/CtfQUhWR3YNzZS+ngzdPc+ps03TuO7Xjuzn61HzHQPDL1pAvaAu4AZJYvo+uPL9MWt5g/5NsVsHsMO8wAAAABJRU5ErkJggg==';

let current_root = '';
let current_parent = '';
let current_remote = '';
let current_file = '';

function selectFile(path, canDel) {
    current_file = path;
    if (canDel)
        $("#delFileBtn").show();
    else
        $("#delFileBtn").hide();
    // $("#fileUrl0")[0].value = current_remote.replace('clan://', 'http://') + 'file/' + current_file;
    $("#fileUrl1")[0].value = "clan://localhost/" + current_file;
    $("#fileUrl2")[0].value = current_remote + current_file;
    $("#fileInfoDialog").show();
}

function fileToApi(type, live) {
    var url;
    if (type === 1) {
        url = "clan://localhost/" + current_file;
    } else {
        url = current_remote + current_file;
    }
    doAction(live ? 'liveApi' : 'api', { url: url });
}

function hideFileInfo() {
    $("#fileInfoDialog").hide();
}

function listFile(path) {
    $('#loadingToast').show();
    $.get('/file/' + path, function (res) {
        let info = JSON.parse(res);
        let parent = info.parent;
        let canDel = info.del === 1;
        current_root = path;
        current_parent = parent;
        current_remote = info.remote;
        let array = info.files;
        if (path === '' && array.length == 0)
            warnToast('读取本地文件失败，可能没有存储权限');
        clear_list();
        if (parent !== '.')
            add_file(tpl_top(parent));

        if (canDel) {
            $('#delCurFolder').show();
        } else {
            $('#delCurFolder').hide();
        }

        array.forEach(node => {
            if (node.dir === 1) {
                add_file(tpl_dir(node.name, node.time, node.path));
            } else {
                add_file(tpl_file(node.name, node.time, node.path, canDel));
            }
        });
        $('#loadingToast').hide();
    })
}

function warnToast(msg) {
    $('#warnToastContent').html(msg);
    $('#warnToast').show();
    setTimeout(() => {
        $('#warnToast').hide();
    }, 1000);
}

function uploadFile() {
    $('#file_uploader').click();
}

function uploadTip() {
    let files = $('#file_uploader')[0].files;
    if (files.length <= 0)
        return false;
    let tip = '';
    for (var i = 0; i < files.length; i++) {
        tip += (files[i].name) + ',';
    }
    $('#uploadTipContent').html(tip);
    $('#uploadTip').show();
}

function doUpload(yes) {
    $('#uploadTip').hide();
    if (yes == 1) {
        let files = $('#file_uploader')[0].files;
        if (files.length <= 0)
            return false;
        var formData = new FormData();
        formData.append('path', current_root);
        for (i = 0; i < files.length; i++) {
            formData.append("files-" + i, files[i]);
        }
        $('#loadingToast').show();
        $.ajax({
            url: '/upload',
            type: 'post',
            data: formData,
            processData: false,
            contentType: false,
            complete: function () {
                $('#loadingToast').hide();
                listFile(current_root);
            }
        });
    }
}

function newFolder() {
    $('#newFolder').show();
}

function doNewFolder(yes) {
    $('#newFolder').hide();
    if (yes == 1) {
        let name = $('#newFolderContent')[0].value.trim();
        if (name.length <= 0)
            return false;
        $('#loadingToast').show();
        $.post('/newFolder', { path: current_root, name: '' + name }, function (data) {
            $('#loadingToast').hide();
            listFile(current_root);
        });
    }
}


function delFolder() {
    $('#delFolderContent').html('是否删除 ' + current_root);
    $('#delFolder').show();
}

function doDelFolder(yes) {
    $('#delFolder').hide();
    if (yes == 1) {
        $('#loadingToast').show();
        $.post('/delFolder', { path: current_root }, function (data) {
            $('#loadingToast').hide();
            listFile(current_parent);
        });
    }
}

function delFolder() {
    $('#delFolderContent').html('是否删除 ' + current_root);
    $('#delFolder').show();
}

function doDelFolder(yes) {
    $('#delFolder').hide();
    if (yes == 1) {
        $('#loadingToast').show();
        $.post('/delFolder', { path: current_root }, function (data) {
            $('#loadingToast').hide();
            listFile(current_parent);
        });
    }
}

function delFile() {
    hideFileInfo();
    $('#delFileContent').html('是否删除 ' + current_file);
    $('#delFile').show();
}

function doDelFile(yes) {
    $('#delFile').hide();
    if (yes == 1) {
        $('#loadingToast').show();
        $.post('/delFile', { path: current_file }, function (data) {
            $('#loadingToast').hide();
            listFile(current_root);
        });
    }
}

function showPanel(id) {
    let tab = $('#tab' + id)[0];
    $(tab).attr('aria-selected', 'true').addClass('weui-bar__item_on');
    $(tab).siblings('.weui-bar__item_on').removeClass('weui-bar__item_on').attr('aria-selected', 'false');
    var panelId = '#' + $(tab).attr('aria-controls');
    if (id === 3 && current_remote.length === 0) {
        listFile('')
    }
    $(panelId).css('display', 'block');
    $(panelId).siblings('.weui-tab__panel').css('display', 'none');
}

$(function () {
    $('.weui-tabbar__item').on('click', function () {
        showPanel(parseInt($(this).attr('id').substr(3)));
    });
});

var url = window.location.href;
if (url.indexOf('remote.html') > 0)
    showPanel(4);
else if (url.indexOf('push.html') > 0)
    showPanel(2);
else if (url.indexOf('api.html') > 0)
    showPanel(3);
else if (url.indexOf('all.html') > 0)
    showPanel(3);
else
    showPanel(1);

/* ============================================================
   手机遥控（遥控面板）
   按键不经过网页的焦点体系，直接 POST 给电视，由电视派发成真实 KeyEvent，
   所以电视那边焦点怎么走完全由电视自己决定，和真遥控器一致。
   ============================================================ */
var RC_KEY = {
    up: 19, down: 20, left: 21, right: 22, ok: 23,
    back: 4, home: 3, menu: 82, play: 85
};
var RC_KEY_NAME = {
    19: '上', 20: '下', 21: '左', 22: '右', 23: '确定',
    4: '返回', 3: '主页', 82: '菜单', 85: '播放'
};
var RC_HOLD_DELAY = 320; // 按住多久开始连发
var RC_HOLD_GAP = 90;   // 连发间隔

function rcStatus(text) {
    var el = document.getElementById('rcSpeed');
    if (el) el.textContent = text;
}

function sendKey(code) {
    if (navigator.vibrate) { try { navigator.vibrate(12); } catch (e) {} }
    var name = RC_KEY_NAME[code] || code;
    // 按键走 GET（与搜索/推送同通道），POST 兜底；电视端会返回执行回执，直接显示在状态行
    $.get('/action', { do: 'key', code: code }, function (res) {
        rcStatus(name + ' ✓ ' + (res || ''));
    }).fail(function () {
        $.post('/action', { do: 'key', code: code }, function (res) {
            rcStatus(name + ' ✓ ' + (res || ''));
        }).fail(function () {
            rcStatus(name + ' ✗ 发送失败，检查是否同一局域网');
        });
    });
}

function bindRcKeys() {
    var nodes = document.querySelectorAll('.rc__key');
    Array.prototype.forEach.call(nodes, function (el) {
        var code = parseInt(el.getAttribute('data-key'), 10);
        if (!code) return;
        var delayTimer = null, repTimer = null, fired = false;

        function fireOnce() {
            if (fired) return;
            fired = true;
            el.classList.add('is-on');
            sendKey(code);
        }
        function stop() {
            if (delayTimer) { clearTimeout(delayTimer); delayTimer = null; }
            if (repTimer) { clearInterval(repTimer); repTimer = null; }
            el.classList.remove('is-on');
            fired = false;
        }
        function start(e) {
            e.preventDefault();
            fireOnce();
            delayTimer = setTimeout(function () {
                repTimer = setInterval(function () { sendKey(code); }, RC_HOLD_GAP);
            }, RC_HOLD_DELAY);
        }

        el.addEventListener('touchstart', start, { passive: false });
        el.addEventListener('touchend', stop);
        el.addEventListener('touchcancel', stop);
        // 桌面浏览器调试用
        el.addEventListener('mousedown', start);
        el.addEventListener('mouseup', stop);
        el.addEventListener('mouseleave', stop);
    });
}

/* 摇杆：拖动方向 = 方向键，拖多远 = 连发多快，松手立刻停。
   为什么不做陀螺仪空鼠：空鼠要在电视端额外实现一整套鼠标协议，
   而摇杆这套只用已有的按键通道，手感也更像真遥控器。 */
var RC_LEVELS = [
    { max: 0.42, gap: 340, name: '精调' },   // 贴近中心：慢慢挪，适合列表微调
    { max: 0.75, gap: 190, name: '滚动' },
    { max: 1.01, gap: 115, name: '快滚' }
];
var RC_DEAD_ZONE = 16;    // 小于这个半径当作没动，防手抖也防误触
var RC_CENTER_RADIUS = 24; // 落在这个圈里松手 = 确定

function bindRcStick() {
    var stick = document.getElementById('rcStick');
    var ball = document.getElementById('rcBall');
    var speed = document.getElementById('rcSpeed');
    if (!stick || !ball) return;

    var RADIUS = stick.clientWidth / 2;   // 摇杆可拖动半径
    var cx = 0, cy = 0;                   // 摇杆圆心（相对视口）
    var timer = null, curKey = 0, curGap = 0;
    var active = false, moved = false, lastDist = 999;
    var defaultText = speed ? speed.textContent : '';

    function center() {
        var r = stick.getBoundingClientRect();
        cx = r.left + r.width / 2;
        cy = r.top + r.height / 2;
        RADIUS = r.width / 2;
    }

    function levelOf(dist) {
        var ratio = dist / RADIUS;
        for (var i = 0; i < RC_LEVELS.length; i++) {
            if (ratio <= RC_LEVELS[i].max) return RC_LEVELS[i];
        }
        return RC_LEVELS[RC_LEVELS.length - 1];
    }

    function schedule(lv) {
        if (!curKey) return;
        if (lv.gap === curGap && timer) return;
        curGap = lv.gap;
        if (timer) clearInterval(timer);
        timer = setInterval(function () { sendKey(curKey); }, lv.gap);
    }

    function paint(dist, key, lv) {
        ball.style.transform = 'translate(' + dist.dx + 'px,' + dist.dy + 'px)';
        if (speed) {
            speed.textContent = key
                ? (lv ? lv.name + ' · ' + (RC_KEY_NAME[key] || '') : '')
                : defaultText;
        }
    }

    function reset() {
        if (timer) { clearInterval(timer); timer = null; }
        curKey = 0; curGap = 0;
        ball.style.transform = 'translate(0,0)';
        if (speed) speed.textContent = defaultText;
        stick.classList.remove('is-on');
    }

    function down(x, y) {
        center();
        active = true; moved = false; lastDist = 0;
        stick.classList.add('is-on');
        move(x, y);
    }

    function move(x, y) {
        if (!active) return;
        var dx = x - cx, dy = y - cy;
        var dist = Math.sqrt(dx * dx + dy * dy);
        lastDist = dist;
        var ratio = dist / RADIUS;
        if (ratio > 1) { dx = dx / ratio; dy = dy / ratio; dist = RADIUS; }

        ball.style.transform = 'translate(' + dx + 'px,' + dy + 'px)';

        if (dist < RC_DEAD_ZONE) {
            if (timer) { clearInterval(timer); timer = null; }
            curKey = 0; curGap = 0;
            if (speed) speed.textContent = defaultText;
            return;
        }
        moved = true;

        // 摇杆是模拟量，斜着推时按主轴走，斜向移动列表手感更稳
        var key;
        if (Math.abs(dx) > Math.abs(dy)) key = dx > 0 ? RC_KEY.right : RC_KEY.left;
        else key = dy > 0 ? RC_KEY.down : RC_KEY.up;

        var lv = levelOf(dist);
        if (key !== curKey) {
            // 换方向先立刻点一下，不然等一个连发周期才动，会觉得"卡一下"
            if (timer) { clearInterval(timer); timer = null; }
            curKey = key; curGap = 0;
            if (navigator.vibrate) { try { navigator.vibrate(10); } catch (e) {} }
            sendKey(key);
            schedule(lv);
        } else {
            schedule(lv);
        }
        paint({ dx: dx, dy: dy }, key, lv);
    }

    function up() {
        if (!active) return;
        // 松手时还落在中心圈内 → 当成按确定；甩出去了就是"停在那一格"
        if (!moved || lastDist <= RC_CENTER_RADIUS) {
            if (navigator.vibrate) { try { navigator.vibrate(22); } catch (e) {} }
            sendKey(RC_KEY.ok);
        }
        active = false;
        reset();
    }

    // Pointer Events 统一处理触摸和鼠标，老浏览器退回 touch/mouse
    if (window.PointerEvent) {
        stick.addEventListener('pointerdown', function (e) {
            e.preventDefault();
            try { stick.setPointerCapture(e.pointerId); } catch (err) { }
            down(e.clientX, e.clientY);
        });
        stick.addEventListener('pointermove', function (e) { move(e.clientX, e.clientY); });
        stick.addEventListener('pointerup', up);
        stick.addEventListener('pointercancel', up);
    } else {
        stick.addEventListener('touchstart', function (e) {
            var t = e.touches[0]; down(t.clientX, t.clientY); e.preventDefault();
        }, { passive: false });
        stick.addEventListener('touchmove', function (e) {
            var t = e.touches[0]; move(t.clientX, t.clientY); e.preventDefault();
        }, { passive: false });
        stick.addEventListener('touchend', up);
        stick.addEventListener('mousedown', function (e) { down(e.clientX, e.clientY); });
        stick.addEventListener('mousemove', function (e) { move(e.clientX, e.clientY); });
        stick.addEventListener('mouseup', up);
    }
}

/* 语音搜片：浏览器原生 Web Speech，零 SDK 零后端。
   只有 Chrome / Edge / 部分安卓浏览器支持，iOS Safari 不支持，
   所以做成渐进增强，不支持时直接提示去打字，不能让整页挂掉。 */
var voiceRec = null, voiceOn = false;

function toggleVoice() {
    var btn = document.getElementById('btn_voice');
    var SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SR) {
        warnToast('当前浏览器不支持语音输入<br>请用 Chrome / Edge，或直接打字搜索');
        return;
    }
    if (voiceOn && voiceRec) { voiceRec.stop(); return; }

    try {
        voiceRec = new SR();
    } catch (e) {
        warnToast('语音功能初始化失败：' + e.message);
        return;
    }
    voiceRec.lang = 'zh-CN';
    voiceRec.interimResults = false;
    voiceRec.maxAlternatives = 1;
    voiceRec.onresult = function (ev) {
        var text = ev.results[0][0].transcript;
        $('#search_key_word').val(text);
    };
    voiceRec.onerror = function (ev) {
        warnToast('语音识别失败：' + (ev.error || '未知错误'));
    };
    voiceRec.onend = function () {
        voiceOn = false;
        if (btn) btn.classList.remove('is-rec');
    };
    try {
        voiceRec.start();
        voiceOn = true;
        if (btn) btn.classList.add('is-rec');
    } catch (e) {
        warnToast('无法启动语音识别：' + e.message);
    }
}

$(function () {
    bindRcKeys();
    bindRcStick();
});
