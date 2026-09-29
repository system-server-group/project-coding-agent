package com.system_server.ai_demo.database.typehandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

/**
 * {@code timestamptz}（{@link OffsetDateTime}）を「システム標準タイムゾーン（Asia/Tokyo）」のオフセットで読み出す MyBatis の
 * TypeHandler。
 *
 * <p>
 * PostgreSQL の {@code timestamptz} は pgjdbc により常に UTC オフセットの {@link OffsetDateTime} として返るため、
 * サーバーサイド描画（Thymeleaf {@code #temporals}）やメール整形（{@link OffsetDateTime#format}）がそのオフセットの まま出力すると UTC
 * 時刻になる。読み出し時に instant を保ったまま Asia/Tokyo のオフセットへ付け替え、システム前提 （JST）に揃える。値の instant は変えないため、楽観排他の
 * {@code updated_at} 照合（instant 比較）には影響しない。 書き込みは pgjdbc が instant で {@code timestamptz}
 * へ格納するため、オフセットを保持したまま渡す。
 *
 * <p>
 * {@code mybatis.type-handlers-package} で本パッケージを走査させることで、全 {@link OffsetDateTime} の既定
 * ハンドラとして登録される（MyBatis 組込みハンドラを置き換える）。
 */
@MappedTypes(OffsetDateTime.class)
public class JstOffsetDateTimeTypeHandler extends BaseTypeHandler<OffsetDateTime> {

    /** システム標準タイムゾーン（アーキテクチャ設計書「システム前提」＝Asia/Tokyo）。 */
    private static final ZoneId SYSTEM_ZONE = ZoneId.of("Asia/Tokyo");

    @Override
    public void setNonNullParameter(
            PreparedStatement ps,
            int i,
            OffsetDateTime parameter,
            JdbcType jdbcType) throws SQLException {
        // instant を保持したまま渡す（timestamptz は instant で格納されるためオフセット表現は不問）。
        ps.setObject(i, parameter);
    }

    @Override
    public OffsetDateTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toSystemZone(rs.getObject(columnName, OffsetDateTime.class));
    }

    @Override
    public OffsetDateTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toSystemZone(rs.getObject(columnIndex, OffsetDateTime.class));
    }

    @Override
    public OffsetDateTime getNullableResult(CallableStatement cs, int columnIndex)
            throws SQLException {
        return toSystemZone(cs.getObject(columnIndex, OffsetDateTime.class));
    }

    /**
     * instant を保ったままシステム標準タイムゾーンのオフセットへ付け替える。
     *
     * @param value 読み出した日時（UTC オフセット。{@code null} 可）
     * @return システム標準タイムゾーンのオフセットに揃えた日時（入力が {@code null} の場合は {@code null}）
     */
    private static OffsetDateTime toSystemZone(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(SYSTEM_ZONE).toOffsetDateTime();
    }
}
