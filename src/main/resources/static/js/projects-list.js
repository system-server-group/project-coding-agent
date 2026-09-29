(function () {
    'use strict';

    var LAYOUT_KEY = 'projectList.columnState';
    var CONDITION_KEY = 'projectList.condition';
    var SEARCH_URL = '/api/projects/search';

    // 固定列数は常に2列で一定（設計「固定する列の数は2列である」）。
    var PINNED_COUNT = 2;

    // 既定の検索条件: 保存が無い場合はステータス オープン/交渉中、キーワード対象は件名。
    var DEFAULT_CONDITION = {
        keyword: '',
        keywordTargets: ['title'],
        statuses: ['OPEN', 'NEGOTIATING'],
        startDateFrom: null, startDateTo: null,
        endDateFrom: null, endDateTo: null,
        registeredDateFrom: null, registeredDateTo: null,
        registrant: '', registrantDepartment: '', client: '',
        bpOnly: false
    };

    // 案件情報テーブルの既定レイアウト（列順・ヘッダー・幅、左2列固定）。
    // 【管理コード】列は locked: 移動不可・常時最左・固定ロック（設計「移動不可で常に固定2列の左側」）。
    var COLUMNS = [
        {field: 'managementCode', headerName: 'ID', width: 80, pinned: 'left', sort: 'desc', locked: true, numeric: true},
        {field: 'title', headerName: '件名', width: 220, pinned: 'left'},
        {field: 'status', headerName: 'ステータス', width: 100, kind: 'code'},
        {field: 'createdBy', headerName: '登録者', width: 110},
        {field: 'createdDepartment', headerName: '登録部署', width: 100},
        {field: 'clientName', headerName: '取引先', width: 200},
        {field: 'commercialFlow', headerName: '商流', width: 150},
        {field: 'summary', headerName: '案件概要', width: 350, kind: 'multiline'},
        {field: 'startDate', headerName: '開始日', width: 110, kind: 'date'},
        {field: 'endDate', headerName: '終了日', width: 110, kind: 'date'},
        {field: 'skill', headerName: 'スキル', width: 200},
        {field: 'process', headerName: '工程', width: 170},
        {field: 'headcount', headerName: '人数', width: 80},
        {field: 'remainingCount', headerName: '残数', width: 80},
        {field: 'bpRecruitment', headerName: 'BP募集(要否)', width: 115, kind: 'code'},
        {field: 'bpRecruitmentDetail', headerName: 'BP募集(詳細)', width: 170},
        {field: 'workLocation', headerName: '作業場所', width: 170},
        {field: 'estimatedUnitPrice', headerName: '見込単価', width: 170},
        {field: 'contractType', headerName: '契約種別', width: 100, kind: 'code'},
        {field: 'note', headerName: '備考', width: 170, kind: 'multiline'},
        {field: 'createdAt', headerName: '登録日時', width: 170, kind: 'datetime'},
        {field: 'updatedBy', headerName: '更新者', width: 110},
        {field: 'updatedDepartment', headerName: '更新部署', width: 110},
        {field: 'updatedAt', headerName: '更新日時', width: 170, kind: 'datetime'}
    ];

    var HEADER_BY_FIELD = {};
    COLUMNS.forEach(function (c) { HEADER_BY_FIELD[c.field] = c.headerName; });

    // ---- 値の整形（区分=表示名／日付=YYYY/MM/DD／日時=YYYY/MM/DD hh:mm:ss／改行=半角スペース） ----

    function cellText(field, kind, data, forCsv) {
        var value = data ? data[field] : null;
        if (value === null || value === undefined || value === '') {
            return '';
        }
        if (kind === 'code') {
            return value.label || '';
        }
        if (kind === 'date') {
            return String(value).substring(0, 10).replace(/-/g, '/');
        }
        if (kind === 'datetime') {
            var s = String(value);
            return s.substring(0, 10).replace(/-/g, '/') + ' ' + s.substring(11, 19);
        }
        if (kind === 'multiline') {
            // 一覧表示は改行を半角スペースへ置換するが、CSV は設計どおり改行（LF）を保持し quote で囲む。
            return forCsv ? String(value).replace(/\r\n?/g, '\n')
                : String(value).replace(/\r?\n/g, ' ');
        }
        return String(value);
    }

    // BP募集(要否)セルをモックアップのバッジ（必要=bp-pill／不要・null=bp-none）で描画する。
    function bpRecruitmentRenderer(params) {
        var value = params.data ? params.data.bpRecruitment : null;
        var span = document.createElement('span');
        if (value && value.code === 'REQUIRED') {
            span.className = 'bp-pill';
            span.textContent = value.label || '必要';
        } else if (value && value.code === 'NOT_REQUIRED') {
            span.className = 'bp-none';
            span.textContent = value.label || '不要';
        } else {
            span.className = 'bp-none';
            span.textContent = '—';
        }
        return span;
    }

    var CELL_RENDERERS = {bpRecruitment: bpRecruitmentRenderer};

    // 数値列（管理コード）は文字列比較を避け数値で比較する（"9" > "40" と誤ソートされるのを防ぐ）。
    function numericComparator(a, b) {
        return Number(a) - Number(b);
    }

    function buildColumnDefs() {
        return COLUMNS.map(function (c) {
            return {
                colId: c.field,
                field: c.field,
                headerName: c.headerName,
                width: c.width,
                minWidth: 80,
                pinned: c.pinned || null,
                sort: c.sort || null,
                // 型推論に依存せず明示する。valueGetter が全列で整形済み文字列を返すため text。
                // 管理コードは下の numericComparator で数値順に比較する（text 既定比較は使わない）。
                cellDataType: 'text',
                suppressMovable: c.locked || false,
                lockPosition: c.locked ? 'left' : null,
                lockPinned: c.locked || false,
                cellRenderer: CELL_RENDERERS[c.field] || null,
                comparator: c.numeric ? numericComparator : null,
                valueGetter: function (params) { return cellText(c.field, c.kind, params.data); }
            };
        });
    }

    // ---- 日付入力(ISO: yyyy-mm-dd)。値はそのまま送受信し、空欄は null として送信する ----

    var DATE_KEYS = ['startDateFrom', 'startDateTo', 'endDateFrom', 'endDateTo',
        'registeredDateFrom', 'registeredDateTo'];

    function dateValue(id) { return document.getElementById(id).value || null; }

    // 保存済み条件の日付は ISO(yyyy-mm-dd) のみ有効とし、非ISO(旧形式等)は null に正規化する。
    function isoDateOrNull(value) {
        return /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : null;
    }

    // ---- 検索条件の組立・反映・保存 ----

    function checkedValues(ids) {
        return ids.filter(function (id) {
            var el = document.getElementById(id);
            return el && el.checked;
        }).map(function (id) { return document.getElementById(id).value; });
    }

    var KEYWORD_TARGET_IDS = ['kwTitle', 'kwSkill', 'kwFlow', 'kwLoc', 'kwSummary', 'kwNote'];
    var STATUS_IDS = ['stOpen', 'stNego', 'stClosed'];

    function buildCondition() {
        return {
            keyword: document.getElementById('keyword').value,
            keywordTargets: checkedValues(KEYWORD_TARGET_IDS),
            statuses: checkedValues(STATUS_IDS),
            startDateFrom: dateValue('startDateFrom'),
            startDateTo: dateValue('startDateTo'),
            endDateFrom: dateValue('endDateFrom'),
            endDateTo: dateValue('endDateTo'),
            registeredDateFrom: dateValue('registeredDateFrom'),
            registeredDateTo: dateValue('registeredDateTo'),
            registrant: document.getElementById('registrant').value,
            registrantDepartment: document.getElementById('registrantDepartment').value,
            client: document.getElementById('client').value,
            bpOnly: document.getElementById('bpOnly').checked
        };
    }

    function applyCondition(c) {
        document.getElementById('keyword').value = c.keyword || '';
        KEYWORD_TARGET_IDS.forEach(function (id) {
            var el = document.getElementById(id);
            el.checked = (c.keywordTargets || []).indexOf(el.value) >= 0;
        });
        STATUS_IDS.forEach(function (id) {
            var el = document.getElementById(id);
            el.checked = (c.statuses || []).indexOf(el.value) >= 0;
        });
        DATE_KEYS.forEach(function (key) {
            document.getElementById(key).value = c[key] || '';
        });
        document.getElementById('registrant').value = c.registrant || '';
        document.getElementById('registrantDepartment').value = c.registrantDepartment || '';
        document.getElementById('client').value = c.client || '';
        document.getElementById('bpOnly').checked = !!c.bpOnly;
    }

    function loadCondition() {
        try {
            var saved = localStorage.getItem(CONDITION_KEY);
            if (saved) {
                var condition = JSON.parse(saved);
                DATE_KEYS.forEach(function (key) {
                    condition[key] = isoDateOrNull(condition[key]);
                });
                return condition;
            }
        } catch (e) {
            // 破損した保存値は無視して既定条件にフォールバックする
        }
        return DEFAULT_CONDITION;
    }

    function saveCondition(c) {
        try {
            localStorage.setItem(CONDITION_KEY, JSON.stringify(c));
        } catch (e) {
            // localStorage が使用できない環境では保持しない
        }
    }

    // ---- レイアウト（列状態）の保存・復元・破棄 ----

    function saveLayout(api) {
        try {
            localStorage.setItem(LAYOUT_KEY, JSON.stringify(api.getColumnState()));
        } catch (e) {
            // 保持しない
        }
    }

    function restoreLayout(api) {
        try {
            var saved = localStorage.getItem(LAYOUT_KEY);
            if (saved) {
                api.applyColumnState({state: JSON.parse(saved), applyOrder: true});
            }
        } catch (e) {
            // 破損状態は無視する
        }
    }

    // 列固定は常に表示順の先頭 PINNED_COUNT 列（設計: 固定は常に2列・左側固定、列順序変更に連動）。
    // 表示順（getColumnState は表示左→右順で返す）の先頭2列を pinned:'left'、他を非固定へ揃える。
    // 列を固定域へ持込んで3列化した場合は先頭2列へ戻し、固定列を可変域へ持出して1列化した場合は
    // 可変列の最左（新たな2列目）を充当して常に2列を維持する（onColumnPinned/onColumnMoved でライブ矯正）。
    // 自身の applyColumnState が onColumnPinned/onColumnMoved を再発火させ得るため pinGuard で再入を防ぐ。
    var pinGuard = false;

    function syncPinnedToOrder(api) {
        var state = api.getColumnState();
        var changes = [];
        state.forEach(function (s, index) {
            var pinned = index < PINNED_COUNT ? 'left' : null;
            if (s.pinned !== pinned) {
                changes.push({colId: s.colId, pinned: pinned});
            }
        });
        if (changes.length) {
            api.applyColumnState({state: changes});
        }
    }

    // ---- エラー表示 ----

    function clearErrors() {
        var spans = document.querySelectorAll('[data-error-for]');
        Array.prototype.forEach.call(spans, function (s) { s.textContent = ''; });
    }

    // 入力値チェックのエラーは、該当する画面項目（部品）の直下に表示する（画面部品仕様に準拠）。
    function showErrors(errors) {
        (errors || []).forEach(function (err) {
            var span = err.field
                ? document.querySelector('[data-error-for="' + err.field + '"]') : null;
            if (span) {
                span.textContent = err.message;
            }
        });
    }

    // ---- 検索実行 ----

    var gridApi = null;

    function csrfHeaders() {
        var headers = {'Content-Type': 'application/json'};
        var token = document.querySelector('meta[name="_csrf"]');
        var header = document.querySelector('meta[name="_csrf_header"]');
        if (token && header && header.content) {
            headers[header.content] = token.content;
        }
        return headers;
    }

    // 未認証(401=セッション切れ)時の既定動作。API共通仕様に従いログイン画面へ遷移する。
    // ドメインURL ( / ) へ直接遷移するため復帰先は保存されず、ログイン成功後は案件情報一覧画面へ
    // 遷移する（案件情報一覧機能 機能仕様書「ログインの有効期限が切れている場合」）。
    function redirectToLogin() {
        window.location.assign('/');
    }

    function runSearch(condition) {
        clearErrors();
        fetch(SEARCH_URL, {
            method: 'POST',
            headers: csrfHeaders(),
            body: JSON.stringify(condition)
        }).then(function (response) {
            if (response.status === 401) {
                // セッション切れ。ログイン画面へ誘導する。
                redirectToLogin();
                return null;
            }
            if (response.status === 400) {
                return response.json().then(function (body) {
                    showErrors(body.errors);
                    return null;
                });
            }
            if (!response.ok) {
                // その他のエラーは応答のステータスのエラー画面へ遷移する（API共通仕様 システム共通仕様書）。
                window.location.href = '/error/' + response.status;
                return null;
            }
            return response.json();
        }).then(function (body) {
            if (body === null) { return; }
            var rows = body.projects || [];
            if (gridApi) {
                gridApi.setGridOption('rowData', rows);
                applyPageSize();
            }
            setDataCount(rows.length);
            saveCondition(condition);
        }).catch(function () {
            window.location.href = '/error';
        });
    }

    // ---- CSV ダウンロード（クライアント完結。表示中の列順・ソート順で出力） ----

    function quote(value) {
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0
            || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            return '"' + value.replace(/"/g, '""') + '"';
        }
        return value;
    }

    function kindOf(field) {
        var col = COLUMNS.filter(function (c) { return c.field === field; })[0];
        return col ? col.kind : null;
    }

    function downloadCsv() {
        if (!gridApi) { return; }
        var displayed = gridApi.getColumnState().filter(function (s) { return !s.hide; });
        var fields = displayed.map(function (s) { return s.colId; });
        var lines = [fields.map(function (f) { return quote(HEADER_BY_FIELD[f] || f); }).join(',')];
        gridApi.forEachNodeAfterFilterAndSort(function (node) {
            var cells = fields.map(function (f) {
                return quote(cellText(f, kindOf(f), node.data, true));
            });
            lines.push(cells.join(','));
        });
        var content = '﻿' + lines.join('\r\n') + '\r\n';
        var blob = new Blob([content], {type: 'text/csv'});
        var url = URL.createObjectURL(blob);
        var a = document.createElement('a');
        a.href = url;
        a.download = '案件情報一覧.csv';
        a.click();
        URL.revokeObjectURL(url);
    }

    function resetLayout() {
        if (!gridApi) { return; }
        try { localStorage.removeItem(LAYOUT_KEY); } catch (e) { /* noop */ }
        gridApi.resetColumnState();
        gridApi.applyColumnState({
            state: [{colId: 'managementCode', sort: 'desc'}],
            defaultState: {sort: null}
        });
    }

    // ---- ページャ（モックアップ .pager を ag-grid API と連動して再現） ----

    // 取得データ件数を全ページャ（上部・下部）へ反映する。
    function setDataCount(count) {
        var els = document.querySelectorAll('[data-data-count]');
        Array.prototype.forEach.call(els, function (el) { el.textContent = count; });
    }

    // 表示件数セレクト（10/20/30/全て）を ag-grid のページサイズへ反映する。
    // sourceValue 未指定時は先頭セレクトの値を採用し、全ページャのセレクトを同期する。
    function applyPageSize(sourceValue) {
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
    function renderPagerNums(nums, totalPages, current) {
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
    function renderPager() {
        if (!gridApi) { return; }
        var totalPages = gridApi.paginationGetTotalPages();
        var current = gridApi.paginationGetCurrentPage();
        var pages = Math.max(totalPages, 1);

        Array.prototype.forEach.call(document.querySelectorAll('[data-pager-nums]'),
            function (nums) { renderPagerNums(nums, totalPages, current); });

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

    // ---- ソート（ダブルクリック契機） ----

    // 詳細設計に合わせ、列ヘッダーの「ダブルクリック」でソートを 昇順→降順→なし に切り替える。
    // ag-Grid 標準のシングルクリックソートは抑止する（複数列ソートは行わない）。
    function enableDoubleClickSort(gridDiv, getApi) {
        // ソートラベル上のシングルクリックは既定ソートを発火させないよう抑止する。
        gridDiv.addEventListener('click', function (event) {
            if (event.target.closest('.ag-header-cell-label')) {
                event.stopPropagation();
            }
        }, true);
        // ダブルクリックした列のソート状態をトグルする。別列適用時は既存ソートを解除する。
        gridDiv.addEventListener('dblclick', function (event) {
            var label = event.target.closest('.ag-header-cell-label');
            if (!label) { return; }
            var cell = label.closest('.ag-header-cell');
            var colId = cell ? cell.getAttribute('col-id') : null;
            var api = getApi();
            if (!colId || !api) { return; }
            var current = null;
            var state = api.getColumnState();
            for (var i = 0; i < state.length; i++) {
                if (state[i].colId === colId) { current = state[i].sort; break; }
            }
            var next = current === 'asc' ? 'desc' : (current === 'desc' ? null : 'asc');
            api.applyColumnState({state: [{colId: colId, sort: next}], defaultState: {sort: null}});
        });
    }

    // ---- 初期化 ----

    function init() {
        var gridDiv = document.getElementById('projectGrid');
        if (!gridDiv || !window.agGrid) { return; }

        var gridOptions = {
            columnDefs: buildColumnDefs(),
            rowData: [],
            // 行高・ヘッダ高はモック(.mk-table)の 34px/38px に合わせる（CSS変数では確実に効かないため明示）。
            rowHeight: 34,
            headerHeight: 38,
            pagination: true,
            paginationPageSize: 30,
            suppressPaginationPanel: true,
            // 列ヘッダーを表外へドロップしても列を非表示にしない（位置は元に戻る）。
            suppressDragLeaveHidesColumns: true,
            defaultColDef: {sortable: true, resizable: true},
            overlayNoRowsTemplate: '該当するデータがありません',
            onGridReady: function (event) {
                gridApi = event.api;
                restoreLayout(gridApi);
                // 保存値が旧固定モデル・破損で固定列が表示順先頭2列と一致しなくても矯正する（保存はしない）。
                pinGuard = true;
                try { syncPinnedToOrder(gridApi); } finally { pinGuard = false; }
                var condition = loadCondition();
                applyCondition(condition);
                runSearch(condition);
            },
            onPaginationChanged: function () { renderPager(); },
            onRowClicked: function (event) {
                if (event.data && event.data.managementCode != null) {
                    window.location.href = '/projects/detail/' + event.data.managementCode;
                }
            },
            onSortChanged: function (event) { saveLayout(event.api); },
            // 列順序変更の確定時に固定列を表示順先頭2列へ連動させ、レイアウトを保存する。
            onColumnMoved: function (event) {
                if (!event.finished || pinGuard) { return; }
                pinGuard = true;
                try { syncPinnedToOrder(event.api); } finally { pinGuard = false; }
                saveLayout(event.api);
            },
            // 列の固定/解除（固定域への持込・持出）の度に固定を先頭2列へライブ矯正する。
            // 持込で3列化→2列へ、持出で1列化→可変列の最左を充当して2列を維持する。
            onColumnPinned: function (event) {
                if (pinGuard) { return; }
                pinGuard = true;
                try { syncPinnedToOrder(event.api); } finally { pinGuard = false; }
                saveLayout(event.api);
            },
            onColumnResized: function (event) { if (event.finished) { saveLayout(event.api); } }
        };
        window.agGrid.createGrid(gridDiv, gridOptions);
        enableDoubleClickSort(gridDiv, function () { return gridApi; });

        document.getElementById('searchToggle').addEventListener('click', function () {
            document.getElementById('searchPanel').classList.toggle('open');
        });
        document.getElementById('searchButton').addEventListener('click', function () {
            runSearch(buildCondition());
        });
        document.getElementById('csvDownloadButton').addEventListener('click', downloadCsv);
        document.getElementById('resetLayoutButton').addEventListener('click', resetLayout);
        Array.prototype.forEach.call(document.querySelectorAll('[data-page-size]'),
            function (sel) {
                sel.addEventListener('change', function () { applyPageSize(this.value); });
            });
        Array.prototype.forEach.call(document.querySelectorAll('[data-page-jump]'),
            function (sel) {
                sel.addEventListener('change', function () {
                    gridApi.paginationGoToPage(parseInt(this.value, 10));
                });
            });
    }

    init();
})();
