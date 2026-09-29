(function () {
    'use strict';

    var MAX_SIZE = 6 * 1024 * 1024;
    var MAX_NAME = 200;
    var MAX_ATTACHMENTS = 5;

    var editProtect = document.getElementById('editProtect');
    var editFields = document.querySelectorAll('.edit-field');
    var editOnly = document.querySelectorAll('.edit-only');
    var submitButton = document.getElementById('submitButton');
    var managementCodeInput = document.getElementById('managementCode');
    var managementCode = managementCodeInput ? managementCodeInput.value : '';

    // 【ファイル一覧】は添付ファイルAPI（添付一覧）から取得して描画するため、行は都度取得する。
    // 一覧は attachmentPicker の直前に置く（0件時は一覧そのものを DOM に出さない）。
    var attachmentPicker = document.getElementById('attachmentPicker');
    var attachmentsLoaded = false;

    function attachmentRows() {
        return document.querySelectorAll('#attachmentList .attachment-row');
    }

    function isProtected() {
        return !editProtect || editProtect.checked;
    }

    // ファイルピッカーの読み取り専用条件。編集保護オンに加え、【ファイル一覧】の取得が完了するまでは
    // 登録済みの添付件数（5件まで）を判定できないため、ファイルの選択・ドロップを受け付けない。
    function isPickerReadonly() {
        return isProtected() || !attachmentsLoaded;
    }

    // 編集保護の状態に応じて入力可否・メール領域/確定ボタン・行クリック挙動を切り替える。
    function applyProtection() {
        var protectedOn = isProtected();
        Array.prototype.forEach.call(editFields, function (el) {
            el.disabled = protectedOn;
        });
        Array.prototype.forEach.call(editOnly, function (el) {
            el.style.display = protectedOn ? 'none' : '';
        });
        // 確定ボタンは保護オン時も下部ツールバー内の位置（幅）を保持したまま隠す。
        // display:none だと戻るボタンが中央寄せで移動するため visibility で切り替える。
        if (submitButton) {
            submitButton.style.visibility = protectedOn ? 'hidden' : '';
        }
        // 添付一覧のアクション表示を行クリック挙動（保護オン=ダウンロード／オフ=削除）に合わせる。
        Array.prototype.forEach.call(attachmentRows(), function (row) {
            var action = row.querySelector('[data-att-action]');
            if (action) {
                action.textContent = protectedOn ? 'ダウンロード' : '削除';
            }
        });
        // 添付ファイルピッカーは常時表示のまま、読み取り専用時は選択・ドロップ不可にする。
        var readonly = isPickerReadonly();
        var drop = document.getElementById('attachmentDrop');
        if (drop) {
            drop.classList.toggle('is-readonly', readonly);
        }
        var fileInput = document.getElementById('attachment');
        if (fileInput) {
            fileInput.disabled = readonly;
        }
    }
    if (editProtect) {
        editProtect.addEventListener('change', applyProtection);
    }
    applyProtection();

    // メール送信チェックボックスに応じてメール本文の表示・非表示を切り替える。
    // 非表示時は textarea を disabled にして送信対象から外し、非表示のまま入力値チェック違反が発生するのを防ぐ。
    var sendMail = document.getElementById('sendMail');
    var mailBodyArea = document.getElementById('mailBodyArea');
    var mailBody = mailBodyArea ? mailBodyArea.querySelector('textarea') : null;
    function syncMailBody() {
        if (sendMail && mailBodyArea) {
            mailBodyArea.style.display = sendMail.checked ? '' : 'none';
            if (mailBody) {
                mailBody.disabled = !sendMail.checked;
            }
        }
    }
    if (sendMail) {
        sendMail.addEventListener('change', syncMailBody);
        syncMailBody();
    }

    // 添付ファイル: ドロップ領域（クリック選択＋ドラッグ&ドロップ）と形式チェック
    // （登録済み5件未満・フォルダ不可・容量6MB以下・ファイル名200字以下）。違反時は選択を解除しエラー表示。
    var DROP_TEXT = 'クリックでファイル選択 ／ ドラッグ＆ドロップ';
    var attachment = document.getElementById('attachment');
    var attachmentError = document.getElementById('attachmentError');
    var dropArea = document.getElementById('attachmentDrop');
    var dropIcon = dropArea ? dropArea.querySelector('.drop-icon') : null;
    var dropMain = dropArea ? dropArea.querySelector('.drop-main') : null;

    // 選択状態に応じてドロップ領域の表示（アイコン・ファイル名・解除リンク）を描き替える。
    function renderDrop() {
        if (!dropArea) {
            return;
        }
        if (attachment.files.length > 0) {
            if (dropIcon) {
                dropIcon.textContent = '📄';
            }
            if (dropMain) {
                // 「{ファイル名}({ファイルサイズ})」形式（画面部品仕様「指定されたファイルの表示」）
                dropMain.textContent = attachment.files[0].name
                    + '(' + SimsFileSize.format(attachment.files[0].size) + ') ';
                var clear = document.createElement('span');
                clear.className = 'drop-clear';
                clear.textContent = '✕ 選択解除';
                clear.addEventListener('click', function (e) {
                    e.stopPropagation();
                    attachment.value = '';
                    attachmentError.textContent = '';
                    renderDrop();
                });
                dropMain.appendChild(clear);
            }
        } else {
            if (dropIcon) {
                dropIcon.textContent = '⤓';
            }
            if (dropMain) {
                dropMain.textContent = DROP_TEXT;
            }
        }
    }

    // 選択ファイルの形式チェック。違反時はメッセージ、問題なければ空文字を返す。
    // isDirectory はドロップ対象がフォルダの場合 true（ダイアログ選択時は常に false）。
    function validateFile(file, isDirectory) {
        if (attachmentRows().length >= MAX_ATTACHMENTS) {
            return '登録できる添付ファイルは5件までです。';
        }
        if (isDirectory) {
            return 'ファイルを登録してください。';
        }
        if (file.size > MAX_SIZE) {
            return '添付ファイルの容量が大きすぎます(6MBまで)';
        }
        if (file.name.length > MAX_NAME) {
            return 'ファイル名は200字までです。';
        }
        return '';
    }

    // 選択（ダイアログ／ドロップ共通）後の形式チェックとドロップ領域の再描画。
    function handleSelection(isDirectory) {
        attachmentError.textContent = '';
        if (attachment.files.length > 0) {
            var message = validateFile(attachment.files[0], isDirectory);
            if (message) {
                attachment.value = '';
                attachmentError.textContent = message;
            }
        }
        renderDrop();
    }

    if (attachment) {
        attachment.addEventListener('change', function () {
            handleSelection(false);
        });
    }

    if (dropArea && attachment) {
        dropArea.addEventListener('click', function () {
            // 読み取り専用（編集保護オン・一覧の取得前）はファイル選択ダイアログを開かない。
            if (isPickerReadonly()) {
                return;
            }
            attachment.click();
        });
        ['dragenter', 'dragover'].forEach(function (type) {
            dropArea.addEventListener(type, function (e) {
                e.preventDefault();
                e.stopPropagation();
                dropArea.classList.add('dragover');
            });
        });
        ['dragleave', 'dragend'].forEach(function (type) {
            dropArea.addEventListener(type, function (e) {
                e.preventDefault();
                e.stopPropagation();
                dropArea.classList.remove('dragover');
            });
        });
        dropArea.addEventListener('drop', function (e) {
            e.preventDefault();
            e.stopPropagation();
            dropArea.classList.remove('dragover');
            // 読み取り専用（編集保護オン・一覧の取得前）はドロップされたファイルを受け付けない。
            if (isPickerReadonly()) {
                return;
            }
            if (!e.dataTransfer || e.dataTransfer.files.length === 0) {
                return;
            }
            // ドロップ経路ではフォルダも指定できてしまうため、先頭アイテムがフォルダかを判定する。
            var isDirectory = false;
            var items = e.dataTransfer.items;
            if (items && items.length > 0 && items[0].webkitGetAsEntry) {
                var entry = items[0].webkitGetAsEntry();
                if (entry) {
                    isDirectory = entry.isDirectory;
                }
            }
            // 添付は1件のみ。複数ドロップ時は先頭のみ採用する。
            var dt = new DataTransfer();
            dt.items.add(e.dataTransfer.files[0]);
            attachment.files = dt.files;
            handleSelection(isDirectory);
        });
    }

    function csrfHeaders() {
        var headers = {};
        var token = document.querySelector('meta[name="_csrf"]');
        var header = document.querySelector('meta[name="_csrf_header"]');
        if (token && header && header.content) {
            headers[header.content] = token.content;
        }
        return headers;
    }

    // 未認証(401=セッション切れ)時の既定動作。API共通仕様に従いログイン画面へ遷移する。
    // ドメインURL ( / ) へ直接遷移するため復帰先は保存されず、ログイン成功後は案件情報一覧画面へ
    // 遷移する（案件情報更新画面 画面レイアウト「ログインの有効期限が切れている場合」）。
    function redirectToLogin() {
        window.location.assign('/');
    }

    // 日時の表示（YYYY/MM/DD hh:mm:ss）。APIはJST（Asia/Tokyo）のISO文字列を返すため、
    // 案件情報一覧画面と同様に文字列から日付部・時刻部を取り出して整形する。
    function formatDateTime(value) {
        var text = String(value);
        return text.substring(0, 10).replace(/-/g, '/') + ' ' + text.substring(11, 19);
    }

    // 【ファイル一覧】の1行を生成する。序数は表示順（登録日時昇順）の1始まり。
    function createAttachmentRow(item, ordinal) {
        var row = document.createElement('li');
        row.className = 'attachment-row';
        row.setAttribute('data-file-id', item.fileId);
        row.setAttribute('data-file-name', item.fileName);

        var icon = document.createElement('span');
        icon.className = 'att-icon';
        icon.textContent = '📄';
        row.appendChild(icon);

        var body = document.createElement('div');
        body.className = 'att-body';
        var name = document.createElement('div');
        name.className = 'att-name';
        name.textContent =
            ordinal + '. ' + item.fileName + '(' + SimsFileSize.format(item.fileSize) + ')';
        body.appendChild(name);
        var meta = document.createElement('div');
        meta.className = 'att-meta';
        meta.textContent = item.createdBy + '(' + item.createdDepartment + ') – '
            + formatDateTime(item.createdAt);
        body.appendChild(meta);
        row.appendChild(body);

        var action = document.createElement('span');
        action.className = 'att-action';
        action.setAttribute('data-att-action', '');
        row.appendChild(action);

        row.addEventListener('click', function () {
            handleAttachmentRowClick(row);
        });
        return row;
    }

    // 【ファイル一覧】を描画する。0件の場合は一覧を表示しない（要素ごと取り除く）。
    function renderAttachments(items) {
        var list = document.getElementById('attachmentList');
        if (!items || items.length === 0) {
            if (list) {
                list.parentNode.removeChild(list);
            }
            return;
        }
        if (!list) {
            list = document.createElement('ul');
            list.id = 'attachmentList';
            list.className = 'att-list';
            attachmentPicker.parentNode.insertBefore(list, attachmentPicker);
        }
        list.textContent = '';
        items.forEach(function (item, index) {
            list.appendChild(createAttachmentRow(item, index + 1));
        });
    }

    // 【ファイル一覧】を添付ファイルAPI（添付一覧）から取得して描画する。初期表示と、
    // 削除後・削除済み検知後の再取得で共通に用いる。未認証(401)はログイン画面へ誘導し、
    // その他のエラーは応答のステータスのエラー画面へ遷移する（API共通仕様 システム共通仕様書）。
    function loadAttachments() {
        fetch('/api/attachments?managementCode=' + encodeURIComponent(managementCode))
            .then(function (response) {
                if (response.status === 401) {
                    // セッション切れ。ログイン画面へ誘導する。
                    redirectToLogin();
                    return null;
                }
                if (!response.ok) {
                    window.location.href = '/error/' + response.status;
                    return null;
                }
                return response.json();
            }).then(function (items) {
                if (!items) {
                    return;
                }
                renderAttachments(items);
                attachmentsLoaded = true;
                // 行のアクション表示とピッカーの読み取り専用状態を最新の一覧に合わせる。
                applyProtection();
            }).catch(function () {
                window.location.href = '/error';
            });
    }

    // 添付ダウンロード: fetch でHTTPステータスを判定し、成功時は Blob として保存する。
    // 未認証(401)はログイン画面へ誘導し、該当なし(404=削除済み)はダウンロードを実施せず
    // 【ファイル一覧】のみを再取得する（ユーザへの通知は行わない）。
    // その他のエラーは応答のステータスのエラー画面へ遷移する。
    function downloadAttachment(fileId, fileName) {
        if (!fileId) {
            // ファイルIDが供給されない（画面改ざん等）＝通常起こりえないためエラー画面へ。
            window.location.href = '/error';
            return;
        }
        fetch('/api/attachments/' + fileId).then(function (response) {
            if (response.status === 401) {
                // セッション切れ。ログイン画面へ誘導する。
                redirectToLogin();
                return null;
            }
            if (response.status === 404) {
                // 既に削除済み。通知せず【ファイル一覧】のみを最新化する。
                loadAttachments();
                return null;
            }
            if (!response.ok) {
                window.location.href = '/error/' + response.status;
                return null;
            }
            return response.blob();
        }).then(function (blob) {
            if (!blob) {
                return;
            }
            var url = URL.createObjectURL(blob);
            var a = document.createElement('a');
            a.href = url;
            a.download = fileName || '';
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            URL.revokeObjectURL(url);
        }).catch(function () {
            window.location.href = '/error';
        });
    }

    // 添付削除: 成功時は【ファイル一覧】のみを再取得する。該当なし（既に削除済み）はAPIが
    // 削除件数0で正常応答するため同じ経路となり、正常完了とみなして通知しない。
    // 未認証(401)はログイン画面へ誘導し（添付ファイルは削除されない）、その他は応答のステータスの
    // エラー画面へ遷移する（CSRFトークン不一致の 403 等）。
    function deleteAttachment(fileId) {
        fetch('/api/attachments/' + fileId, {
            method: 'DELETE',
            headers: csrfHeaders()
        }).then(function (response) {
            if (response.ok) {
                loadAttachments();
            } else if (response.status === 401) {
                redirectToLogin();
            } else {
                window.location.href = '/error/' + response.status;
            }
        }).catch(function () {
            window.location.href = '/error';
        });
    }

    // 添付ファイル行クリック: 編集保護オンはダウンロード、オフは削除確認のうえ削除。
    function handleAttachmentRowClick(row) {
        var fileId = row.getAttribute('data-file-id');
        if (isProtected()) {
            downloadAttachment(fileId, row.getAttribute('data-file-name'));
            return;
        }
        if (!window.confirm('この添付ファイルを削除しますか？')) {
            return;
        }
        deleteAttachment(fileId);
    }

    // 案件情報が表示されている場合のみ【ファイル一覧】を取得する（未検出時はフォーム自体が無い）。
    if (attachmentPicker && managementCode) {
        loadAttachments();
    }
})();
