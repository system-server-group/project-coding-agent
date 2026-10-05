// ファイルサイズの表示（画面部品仕様 システム共通仕様書「ファイルサイズの表示」）を整形する共通部品。
// 1023Bまではバイト数をそのまま、1048575Bまでは1024で、それ以上は1048576で割り、
// いずれも小数点第二位で四捨五入した値に単位を付す。
(function () {
    'use strict';

    function roundToTenth(value) {
        return String(Math.round(value * 10) / 10);
    }

    function format(size) {
        var bytes = Number(size);
        if (!isFinite(bytes)) {
            return '';
        }
        if (bytes < 1024) {
            return bytes + 'B';
        }
        if (bytes < 1024 * 1024) {
            return roundToTenth(bytes / 1024) + 'KB';
        }
        return roundToTenth(bytes / (1024 * 1024)) + 'MB';
    }

    window.SimsFileSize = {
        format: format
    };
})();
