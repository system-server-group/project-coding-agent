(function () {
    'use strict';

    // ログイン画面はログイン成功後には表示されない（LoginCtrl が既認証を /projects へ振り分けるため、
    // このスクリプトが動く＝未認証で新規ログインする直前）。前回セッションの案件一覧検索条件を破棄する。
    // キーは projects-list.js の CONDITION_KEY と一致させること（'projectList.condition'）。
    try {
        localStorage.removeItem('projectList.condition');
    } catch (e) {
        // localStorage が使用できない環境では何もしない
    }

    var MAX_LENGTH = 60;
    var HALF_ALNUM = /^[A-Za-z0-9]*$/;
    // 半角記号は印字可能ASCII（U+0020〜U+007E、半角スペースを含む）。共通の使用文字チェック
    // AllowedCharactersValidator（U+0020〜U+007E 許容）と解釈を一致させる。
    var HALF_ALNUM_SYMBOL = /^[\x20-\x7e]*$/;

    var form = document.getElementById('loginForm');
    var userId = document.getElementById('userId');
    var password = document.getElementById('password');
    var userIdError = document.getElementById('userIdError');
    var passwordError = document.getElementById('passwordError');
    var toggle = document.getElementById('passwordToggle');

    // 入力値チェック（項目ごとに最小番号の1件のみ返す）: 必須入力 → 最大文字数 → 使用文字
    function validateUserId(value) {
        if (value.length === 0) {
            return 'ユーザIDを入力してください。';
        }
        if (value.length > MAX_LENGTH) {
            return MAX_LENGTH + '文字以内で入力してください。';
        }
        if (!HALF_ALNUM.test(value)) {
            return '使用不可能な文字が含まれています。';
        }
        return '';
    }

    function validatePassword(value) {
        if (value.length === 0) {
            return 'パスワードを入力してください。';
        }
        if (value.length > MAX_LENGTH) {
            return MAX_LENGTH + '文字以内で入力してください。';
        }
        if (!HALF_ALNUM_SYMBOL.test(value)) {
            return '使用不可能な文字が含まれています。';
        }
        return '';
    }

    if (form) {
        form.addEventListener('submit', function (event) {
            userIdError.textContent = '';
            passwordError.textContent = '';
            var userIdMessage = validateUserId(userId.value);
            var passwordMessage = validatePassword(password.value);
            if (userIdMessage || passwordMessage) {
                event.preventDefault();
                userIdError.textContent = userIdMessage;
                passwordError.textContent = passwordMessage;
            }
        });
    }

    var EYE = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"'
        + ' stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">'
        + '<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z"/>'
        + '<circle cx="12" cy="12" r="3"/></svg>';
    var EYE_OFF = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor"'
        + ' stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">'
        + '<path d="M3 3l18 18"/>'
        + '<path d="M10.6 6.2A9.9 9.9 0 0 1 12 5c6.5 0 10 7 10 7a16.8 16.8 0 0 1-3.4 4.2'
        + 'M6.6 6.6A16.8 16.8 0 0 0 2 12s3.5 7 10 7a9.7 9.7 0 0 0 4.1-.9"/>'
        + '<path d="M9.9 9.9a3 3 0 0 0 4.2 4.2"/></svg>';

    if (toggle && password) {
        toggle.innerHTML = EYE_OFF;
        toggle.addEventListener('click', function () {
            var show = password.type === 'password';
            password.type = show ? 'text' : 'password';
            toggle.innerHTML = show ? EYE : EYE_OFF;
            toggle.setAttribute('aria-label', show ? 'パスワードを非表示' : 'パスワードを表示');
        });
    }
})();
