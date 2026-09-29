(function () {
    'use strict';

    // 画面部品仕様「オートコンプリート」準拠の共通部品。
    // ネイティブ datalist は表示件数・スクロール・部分一致絞り込みを制御できないため、
    // input[list] を検出し、datalist の候補値を保持したうえで自作のドロップダウンに置き換える。
    // - 一度に8件表示、9件以上は縦スクロール（CSS の max-height + overflow-y）
    // - 入力文字を部分文字列として含む候補のみ抽出（該当なしは非表示）
    // - 下に領域があれば下、無ければ上に配置
    // - 候補クリック／Enter で値を上書き入力

    var inputs = document.querySelectorAll('input[list]');
    if (!inputs.length) {
        return;
    }

    // 候補表示用の共有メニュー（1つを使い回す）。overflow を持つ祖先で切れないよう body 直下に置く。
    var menu = document.createElement('ul');
    menu.className = 'ac-menu';
    menu.style.display = 'none';
    document.body.appendChild(menu);

    var current = null; // 現在対象の input
    var options = [];   // current の候補配列
    var items = [];     // 表示中の li 要素
    var activeIndex = -1; // 上下キーで選択中の位置
    var pointerInMenu = false; // ポインタがメニュー上にあるか（スクロールバー操作中の誤閉じ防止）

    menu.addEventListener('mouseenter', function () {
        pointerInMenu = true;
    });
    menu.addEventListener('mouseleave', function () {
        pointerInMenu = false;
    });

    // input に紐づく datalist から候補値（重複・空を除く）を読み取る。
    function readOptions(input) {
        var id = input.getAttribute('data-ac-list');
        var dataList = id ? document.getElementById(id) : null;
        var values = [];
        if (!dataList) {
            return values;
        }
        Array.prototype.forEach.call(dataList.querySelectorAll('option'), function (op) {
            var v = op.value !== '' ? op.value : (op.getAttribute('value') || op.textContent || '');
            if (v !== '' && values.indexOf(v) === -1) {
                values.push(v);
            }
        });
        return values;
    }

    function close() {
        menu.style.display = 'none';
        menu.innerHTML = '';
        items = [];
        activeIndex = -1;
        current = null;
        options = [];
    }

    // 下方向に十分な領域があれば下、無ければ上に配置する。
    function positionMenu(input) {
        var rect = input.getBoundingClientRect();
        menu.style.minWidth = rect.width + 'px';
        menu.style.left = (rect.left + window.scrollX) + 'px';
        var menuHeight = menu.offsetHeight;
        var spaceBelow = window.innerHeight - rect.bottom;
        if (spaceBelow < menuHeight && rect.top > menuHeight) {
            menu.style.top = (rect.top + window.scrollY - menuHeight) + 'px';
        } else {
            menu.style.top = (rect.bottom + window.scrollY) + 'px';
        }
    }

    function setActive(idx) {
        if (activeIndex >= 0 && items[activeIndex]) {
            items[activeIndex].classList.remove('ac-active');
        }
        activeIndex = idx;
        if (activeIndex >= 0 && items[activeIndex]) {
            items[activeIndex].classList.add('ac-active');
            items[activeIndex].scrollIntoView({ block: 'nearest' });
        }
    }

    // 選択確定：値を上書きし、メニューを閉じてから change を通知する。
    function choose(input, value) {
        input.value = value;
        close();
        input.dispatchEvent(new Event('change', { bubbles: true }));
    }

    // 現在の入力値で候補を絞り込み（部分一致・大文字小文字非依存）、メニューを描き替える。
    function render(input) {
        var query = input.value.trim().toLowerCase();
        var matched = options.filter(function (v) {
            return v.toLowerCase().indexOf(query) !== -1;
        });
        menu.innerHTML = '';
        items = [];
        activeIndex = -1;
        if (matched.length === 0) {
            menu.style.display = 'none';
            return;
        }
        matched.forEach(function (v) {
            var li = document.createElement('li');
            li.className = 'ac-item';
            li.textContent = v;
            li.addEventListener('mousedown', function (e) {
                // input の blur による閉包より先に選択させるため既定動作を止める。
                e.preventDefault();
                choose(input, v);
            });
            menu.appendChild(li);
            items.push(li);
        });
        menu.style.display = '';
        positionMenu(input);
    }

    function open(input) {
        current = input;
        options = readOptions(input);
        render(input);
    }

    Array.prototype.forEach.call(inputs, function (input) {
        // ネイティブ datalist の描画を無効化しつつ、候補データの参照先 id は保持する。
        input.setAttribute('data-ac-list', input.getAttribute('list'));
        input.removeAttribute('list');
        input.setAttribute('autocomplete', 'off');

        input.addEventListener('focus', function () {
            open(input);
        });
        input.addEventListener('click', function () {
            open(input);
        });
        input.addEventListener('input', function () {
            if (current === input) {
                render(input);
            } else {
                open(input);
            }
        });
        input.addEventListener('keydown', function (e) {
            if (current !== input || menu.style.display === 'none') {
                return;
            }
            if (e.key === 'ArrowDown') {
                e.preventDefault();
                setActive(activeIndex + 1 < items.length ? activeIndex + 1 : 0);
            } else if (e.key === 'ArrowUp') {
                e.preventDefault();
                setActive(activeIndex - 1 >= 0 ? activeIndex - 1 : items.length - 1);
            } else if (e.key === 'Enter') {
                if (activeIndex >= 0 && items[activeIndex]) {
                    e.preventDefault();
                    choose(input, items[activeIndex].textContent);
                }
            } else if (e.key === 'Escape') {
                close();
            }
        });
        input.addEventListener('blur', function () {
            // 候補クリック(mousedown)を優先させるため、少し遅延して閉じる。
            // スクロールバー操作などメニュー上での操作中（pointerInMenu）は閉じない。
            window.setTimeout(function () {
                if (current === input && !pointerInMenu) {
                    close();
                }
            }, 120);
        });
    });

    // 外側クリック・スクロール・リサイズでは閉じる（body 直下配置のため位置ずれを避ける）。
    document.addEventListener('mousedown', function (e) {
        if (current && e.target !== current && !menu.contains(e.target)) {
            close();
        }
    });
    window.addEventListener('scroll', function (e) {
        // メニュー自身の内部スクロール（候補一覧のスクロール）では閉じない。
        // ページ／祖先コンテナ側のスクロールで input が動く場合のみ閉じる。
        if (current && !menu.contains(e.target)) {
            close();
        }
    }, true);
    window.addEventListener('resize', function () {
        if (current) {
            close();
        }
    });
})();
