// データを作成・変更するフォームの暗黙送信抑止の共通部品
// （画面部品仕様 システム共通仕様書「フォーム送信の操作」）。
// data-explicit-submit 属性を付けたフォームでは、テキスト等の入力欄で Enter キーを
// 押下してもフォームを送信しない。送信は送信ボタンの明示的な操作（クリック・
// ボタンにフォーカスした状態でのキー押下）でのみ実行される。
(function () {
    'use strict';

    var forms = document.querySelectorAll('form[data-explicit-submit]');
    Array.prototype.forEach.call(forms, function (form) {
        form.addEventListener('keydown', function (e) {
            // IME の変換確定（isComposing）は入力操作であり送信に関与しないため触れない。
            if (e.key !== 'Enter' || e.isComposing) {
                return;
            }
            var tag = e.target.tagName ? e.target.tagName.toLowerCase() : '';
            // テキストエリアの Enter は改行入力、ボタン上の Enter は明示的な押下のため妨げない。
            if (tag === 'textarea' || tag === 'button') {
                return;
            }
            e.preventDefault();
        });
    });
})();
