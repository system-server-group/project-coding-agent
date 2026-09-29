(function () {
    'use strict';

    var rows = window.USER_ROWS || [];

    var ROLE_LABELS = {'0': '0: 一般ユーザ', '9': '9: システム管理者'};

    function roleFormatter(params) {
        return ROLE_LABELS[params.value] || params.value;
    }

    // ロールセルをモックアップのバッジ（管理者=role-admin／一般=role-user）で描画する。
    function roleRenderer(params) {
        var span = document.createElement('span');
        span.textContent = ROLE_LABELS[params.value] || params.value || '';
        if (params.value === '9') {
            span.className = 'role-badge role-admin';
        } else if (params.value === '0') {
            span.className = 'role-badge role-user';
        }
        return span;
    }

    // cellDataType は型推論に依存せず明示する（departmentId=Integer→number、その他=String→text。
    // role は永続化コード文字列を valueFormatter で表示整形するため text）。
    var columnDefs = [
        {field: 'userId', headerName: 'ユーザID', sort: 'asc', flex: 1, minWidth: 80, cellDataType: 'text'},
        {field: 'userName', headerName: 'ユーザ名', flex: 1, minWidth: 80, cellDataType: 'text'},
        {field: 'email', headerName: 'メールアドレス', flex: 1, minWidth: 80, cellDataType: 'text'},
        {field: 'departmentId', headerName: '部署ID', flex: 1, minWidth: 80, cellDataType: 'number'},
        {field: 'role', headerName: 'ロール', flex: 1, minWidth: 80, cellDataType: 'text', valueFormatter: roleFormatter, cellRenderer: roleRenderer}
    ];

    var gridApi = null;

    var gridOptions = {
        columnDefs: columnDefs,
        rowData: rows,
        // 行高・ヘッダ高は案件情報一覧と揃える（モック .mk-table の 34px/38px。CSS変数では効かないため明示）。
        rowHeight: 34,
        headerHeight: 38,
        pagination: true,
        paginationPageSize: 30,
        suppressPaginationPanel: true,
        onPaginationChanged: function () { window.SimsPager.render(gridApi); },
        // 列ヘッダーを表外へドロップしても列を非表示にしない（位置は元に戻る）。
        suppressDragLeaveHidesColumns: true,
        defaultColDef: {sortable: true, resizable: true},
        suppressMovableColumns: false,
        overlayNoRowsTemplate: '該当するデータがありません',
        onGridReady: function (event) {
            gridApi = event.api;
        }
    };

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

    var gridDiv = document.getElementById('userGrid');
    if (gridDiv && window.agGrid) {
        gridApi = window.agGrid.createGrid(gridDiv, gridOptions);
        enableDoubleClickSort(gridDiv, function () { return gridApi; });
        window.SimsPager.setup(gridApi);
    }

    // CSV ダウンロード: 表示中テーブルのソート順をクエリに付与する。
    var downloadButton = document.getElementById('csvDownloadButton');
    if (downloadButton) {
        downloadButton.addEventListener('click', function () {
            var sort = 'userId';
            var order = 'asc';
            if (gridApi) {
                var sorted = gridApi.getColumnState().filter(function (column) {
                    return column.sort;
                });
                if (sorted.length > 0) {
                    sort = sorted[0].colId;
                    order = sorted[0].sort;
                }
            }
            var url = '/master/users/csv?sort=' + encodeURIComponent(sort)
                + '&order=' + encodeURIComponent(order);
            window.location.href = url;
        });
    }

    // CSV アップロード: ボタンでファイル選択ダイアログを開き、選択時にフォーム送信する。
    var uploadButton = document.getElementById('csvUploadButton');
    var fileInput = document.getElementById('csvFileInput');
    var uploadForm = document.getElementById('csvUploadForm');
    if (uploadButton && fileInput && uploadForm) {
        uploadButton.addEventListener('click', function () {
            fileInput.click();
        });
        fileInput.addEventListener('change', function () {
            if (fileInput.files.length > 0) {
                uploadForm.submit();
            }
        });
    }
})();
