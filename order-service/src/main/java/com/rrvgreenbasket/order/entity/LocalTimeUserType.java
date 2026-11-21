package com.rrvgreenbasket.order.entity;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class LocalTimeUserType implements UserType<LocalTime> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public int getSqlType() {
        return Types.TIME;
    }

    @Override
    public Class<LocalTime> returnedClass() {
        return LocalTime.class;
    }

    @Override
    public boolean equals(LocalTime x, LocalTime y) {
        return Objects.equals(x, y);
    }

    @Override
    public int hashCode(LocalTime x) {
        return Objects.hashCode(x);
    }

    @Override
    public LocalTime nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException {
        String str = rs.getString(position);
        if (str == null || str.isBlank()) {
            return null;
        }
        String trimmed = str.trim();
        if (trimmed.length() == 5) {
            return LocalTime.parse(trimmed, DateTimeFormatter.ofPattern("HH:mm"));
        }
        return LocalTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_TIME);
    }

    @Override
    public void nullSafeSet(PreparedStatement st, LocalTime value, int index, SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            st.setNull(index, Types.TIME);
        } else {
            st.setString(index, value.format(FORMATTER));
        }
    }

    @Override
    public LocalTime deepCopy(LocalTime value) {
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(LocalTime value) {
        return value != null ? value.toString() : null;
    }

    @Override
    public LocalTime assemble(Serializable cached, Object owner) {
        return cached != null ? LocalTime.parse((String) cached) : null;
    }

    @Override
    public LocalTime replace(LocalTime detached, LocalTime managed, Object owner) {
        return detached;
    }
}
