// ============================================================================
// screens/mockup.js — 画面ごとモックアップ用の最小インタラクション
//   ・案件検索パネルの開閉
//   ・パスワード表示/非表示(ログイン)
//   ・編集保護 ON/OFF(更新画面) … 読取専用の切替
//   ・メール送信チェックで本文表示(登録/更新)
//   ・BPチェックの詳細欄(視覚のみ)
// 列の並べ替え/リサイズ等のテーブル操作は静的モックアップのため非対応。
// ============================================================================
(function () {
  function ready(fn) {
    if (document.readyState !== 'loading') fn();
    else document.addEventListener('DOMContentLoaded', fn);
  }

  ready(function () {
    // ---- 検索パネル開閉 ----
    document.querySelectorAll('[data-search-toggle]').forEach(function (head) {
      head.addEventListener('click', function () {
        var panel = head.closest('.search-panel');
        if (panel) panel.classList.toggle('open');
      });
    });

    // ---- パスワード表示切替 ----
    var EYE = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/></svg>';
    var EYE_OFF = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M3 3l18 18"/><path d="M10.6 6.2A9.9 9.9 0 0 1 12 5c6.5 0 10 7 10 7a16.8 16.8 0 0 1-3.4 4.2M6.6 6.6A16.8 16.8 0 0 0 2 12s3.5 7 10 7a9.7 9.7 0 0 0 4.1-.9"/><path d="M9.9 9.9a3 3 0 0 0 4.2 4.2"/></svg>';
    document.querySelectorAll('[data-pw-toggle]').forEach(function (btn) {
      var input = document.querySelector(btn.getAttribute('data-pw-toggle'));
      if (!input) return;
      btn.innerHTML = EYE_OFF;
      btn.addEventListener('click', function () {
        var show = input.type === 'password';
        input.type = show ? 'text' : 'password';
        btn.innerHTML = show ? EYE : EYE_OFF;
      });
    });

    // ---- ログイン: 送信(モック) → 一覧へ ----
    document.querySelectorAll('[data-login-form]').forEach(function (form) {
      form.addEventListener('submit', function (e) {
        e.preventDefault();
        window.location.href = 'list.html';
      });
    });

    // ---- 編集保護トグル(更新画面) ----
    var protectToggle = document.querySelector('[data-protect-toggle]');
    if (protectToggle) {
      var setProtect = function (on) {
        var root = document.querySelector('[data-edit-root]');
        if (!root) return;
        root.classList.toggle('protect-on', on);
        // 入力欄の読取専用切替
        root.querySelectorAll('[data-lockable]').forEach(function (el) {
          if (el.tagName === 'SELECT' || el.type === 'checkbox') el.disabled = on;
          else el.readOnly = on;
        });
        // 表示テキスト/ノブ
        var track = protectToggle.querySelector('.toggle-track');
        var knob = protectToggle.querySelector('.toggle-knob');
        var lab = protectToggle.querySelector('[data-protect-label]');
        if (track) track.style.background = on ? 'var(--accent)' : '#bfbfbf';
        if (knob) knob.style.left = on ? '20px' : '2px';
        if (lab) { lab.textContent = on ? 'ON' : 'OFF'; lab.style.color = on ? 'var(--accent)' : '#888'; }
        var submit = root.querySelector('[data-submit]');
        if (submit) submit.disabled = on;
      };
      var state = { on: protectToggle.getAttribute('data-protect-toggle') !== 'off' };
      setProtect(state.on);
      protectToggle.addEventListener('click', function () { state.on = !state.on; setProtect(state.on); });
    }

    // ---- メール送信チェック → 本文表示 ----
    document.querySelectorAll('[data-mail-check]').forEach(function (chk) {
      var body = document.querySelector('[data-mail-body]');
      var sync = function () { if (body) body.style.display = chk.checked ? '' : 'none'; };
      sync();
      chk.addEventListener('change', sync);
    });
  });
})();
