// 取得データ件数／ページ番号（« ‹ 1 2 3 › »）／表示件数／表示ページのページャを
// ag-grid と連動させる共通部品。案件情報一覧（projects-list.js）のページャ実装を汎化したもの。
//
// 前提:
//   - グリッドは pagination:true, suppressPaginationPanel:true（標準パネル抑止）で生成する。
//   - ページャHTML（.pager 内に data-data-count / data-pager-nums / data-page-size /
//     data-page-jump）を表の上部・下部へ配置しておく（上下で同一構成・JSが両方を同期）。
//   - createGrid 後に SimsPager.setup(gridApi) を呼び、gridOptions に
//     onPaginationChanged: function () { SimsPager.render(gridApi); } を設定する。
(function () {
    'use strict';

    // 取得データ件数を全ページャ（上部・下部）へ反映する。
    function setDataCount(count) {
        var els = document.querySelectorAll('[data-data-count]');
        Array.prototype.forEach.call(els, function (el) { el.textContent = count; });
    }

    // 表示件数セレクト（10/20/30/全て）を ag-grid のページサイズへ反映する。
    // sourceValue 未指定時は先頭セレクトの値を採用し、全ページャのセレクトを同期する。
    function applyPageSize(gridApi, sourceValue) {
        if (!gridApi) { return; }
        var selects = document.querySelectorAll('[data-page-size]');
        var value = sourceValue;
        if (value === undefined || value === null) {
            value = selects.length ? selects[0].value : '30';
        }
        Array.prototype.forEach.call(selects, function (el) { el.value = value; });
        var size = (value === 'all')
            ? Math.max(gridApi.getDisplayedRowCount(), 1)
            : parseInt(value, 10);
        gridApi.setGridOption('paginationPageSize', size);
    }

    function addPagerButton(parent, label, disabled, active, onClick) {
        var button = document.createElement('button');
        button.type = 'button';
        button.className = 'pg-btn' + (active ? ' active' : '');
        button.textContent = label;
        if (disabled) {
            button.disabled = true;
        } else if (onClick) {
            button.addEventListener('click', onClick);
        }
        parent.appendChild(button);
    }

    // « ‹ [ページ番号] › » を1つの番号コンテナへ描画する。
    function renderPagerNums(gridApi, nums, totalPages, current) {
        var atFirst = current <= 0;
        var atLast = totalPages === 0 || current >= totalPages - 1;
        nums.innerHTML = '';
        addPagerButton(nums, '«', atFirst, false, function () {
            gridApi.paginationGoToFirstPage();
        });
        addPagerButton(nums, '‹', atFirst, false, function () {
            gridApi.paginationGoToPreviousPage();
        });
        if (totalPages === 0) {
            addPagerButton(nums, '1', true, true, null);
        } else {
            // ページ番号ボタンは現在ページとその前後最大3件のみ配置する。
            var from = Math.max(0, current - 3);
            var to = Math.min(totalPages - 1, current + 3);
            for (var i = from; i <= to; i++) {
                (function (index) {
                    addPagerButton(nums, String(index + 1), false, index === current,
                        function () { gridApi.paginationGoToPage(index); });
                })(i);
            }
        }
        addPagerButton(nums, '›', atLast, false, function () {
            gridApi.paginationGoToNextPage();
        });
        addPagerButton(nums, '»', atLast, false, function () {
            gridApi.paginationGoToLastPage();
        });
    }

    // « ‹ [ページ番号] › » と「表示ページ」セレクトを上部・下部の全ページャへ再描画する。
    function renderPager(gridApi) {
        if (!gridApi) { return; }
        var totalPages = gridApi.paginationGetTotalPages();
        var current = gridApi.paginationGetCurrentPage();
        var pages = Math.max(totalPages, 1);

        Array.prototype.forEach.call(document.querySelectorAll('[data-pager-nums]'),
            function (nums) { renderPagerNums(gridApi, nums, totalPages, current); });

        Array.prototype.forEach.call(document.querySelectorAll('[data-page-jump]'),
            function (jump) {
                jump.innerHTML = '';
                for (var p = 0; p < pages; p++) {
                    var option = document.createElement('option');
                    option.value = String(p);
                    option.textContent = (p + 1) + ' / ' + pages;
                    if (p === current) { option.selected = true; }
                    jump.appendChild(option);
                }
            });
    }

    // ページャを初期化し ag-grid と連動させる。件数は取得行数（フィルタ前の全件）を表示する。
    function setup(gridApi) {
        if (!gridApi) { return; }
        Array.prototype.forEach.call(document.querySelectorAll('[data-page-size]'),
            function (sel) {
                sel.addEventListener('change', function () {
                    applyPageSize(gridApi, this.value);
                });
            });
        Array.prototype.forEach.call(document.querySelectorAll('[data-page-jump]'),
            function (sel) {
                sel.addEventListener('change', function () {
                    gridApi.paginationGoToPage(parseInt(this.value, 10));
                });
            });
        setDataCount(gridApi.getDisplayedRowCount());
        applyPageSize(gridApi);
        renderPager(gridApi);
    }

    window.SimsPager = {
        setup: setup,
        render: renderPager,
        setDataCount: setDataCount,
        applyPageSize: applyPageSize
    };
})();
