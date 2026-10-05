(function () {
    'use strict';

    var MAX_SIZE = 6 * 1024 * 1024;
    var MAX_NAME = 200;

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
    // （フォルダ不可・容量6MB以下・ファイル名200字以下）。違反時は選択を解除しエラー表示。
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
})();
