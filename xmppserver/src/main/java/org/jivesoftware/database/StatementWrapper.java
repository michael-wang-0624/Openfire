/**
 * $RCSfile$
 * $Revision: 37 $
 * $Date: 2004-10-20 23:08:43 -0700 (Wed, 20 Oct 2004) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.database;

import java.sql.*;

/**
 * An implementation of the Statement interface that wraps an underlying
 * Statement object.
 *
 * @author Gaston Dombiak
 */
public abstract class StatementWrapper implements Statement {

    protected Statement stmt;

    /**
     * Creates a new StatementWrapper that wraps {@code stmt}.
     *
     * @param stmt The to-be-wrapped statement.
     */
    public StatementWrapper(Statement stmt) {
        this.stmt = stmt;
    }

    public ResultSet executeQuery(String sql) throws SQLException {
        return stmt.executeQuery(sql);
    }

    public int executeUpdate(String sql) throws SQLException {
        return stmt.executeUpdate(sql);
    }

    public void close() throws SQLException {
        stmt.close();
    }

    public int getMaxFieldSize() throws SQLException {
        return stmt.getMaxFieldSize();
    }

    public void setMaxFieldSize(int max) throws SQLException {
        stmt.setMaxFieldSize(max);
    }

    public int getMaxRows() throws SQLException {
        return stmt.getMaxRows();
    }

    public void setMaxRows(int max) throws SQLException {
        stmt.setMaxRows(max);
    }

    public void setEscapeProcessing(boolean enable) throws SQLException {
        stmt.setEscapeProcessing(enable);
    }

    public int getQueryTimeout() throws SQLException {
        return stmt.getQueryTimeout();
    }

    public void setQueryTimeout(int seconds) throws SQLException {
        stmt.setQueryTimeout(seconds);
    }

    public void cancel() throws SQLException {
        stmt.cancel();
    }

    public SQLWarning getWarnings() throws SQLException {
        return stmt.getWarnings();
    }

    public void clearWarnings() throws SQLException {
        stmt.clearWarnings();
    }

    public void setCursorName(String name) throws SQLException {
        stmt.setCursorName(name);
    }

    public boolean execute(String sql) throws SQLException {
        return stmt.execute(sql);
    }

    public ResultSet getResultSet() throws SQLException {
        return stmt.getResultSet();
    }

    public int getUpdateCount() throws SQLException {
        return stmt.getUpdateCount();
    }

    public boolean getMoreResults() throws SQLException {
        return stmt.getMoreResults();
    }

    public void setFetchDirection(int direction) throws SQLException {
        stmt.setFetchDirection(direction);
    }

    public int getFetchDirection() throws SQLException {
        return stmt.getFetchDirection();
    }

    public void setFetchSize(int rows) throws SQLException {
        stmt.setFetchSize(rows);
    }

    public int getFetchSize() throws SQLException {
        return stmt.getFetchSize();
    }

    public int getResultSetConcurrency() throws SQLException {
        return stmt.getResultSetConcurrency();
    }

    public int getResultSetType() throws SQLException {
        return stmt.getResultSetType();
    }

    public void addBatch(String sql) throws SQLException {
        stmt.addBatch(sql);
    }

    public void clearBatch() throws SQLException {
        stmt.clearBatch();
    }

    public int[] executeBatch() throws SQLException {
        return stmt.executeBatch();
    }

    public Connection getConnection() throws SQLException {
        return stmt.getConnection();
    }

    public boolean getMoreResults(int current) throws SQLException {
        return stmt.getMoreResults(current);
    }

    public ResultSet getGeneratedKeys() throws SQLException {
        return stmt.getGeneratedKeys();
    }

    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return stmt.executeUpdate(sql, autoGeneratedKeys);
    }

    public int executeUpdate(String sql, int columnIndexes[]) throws SQLException {
        return stmt.executeUpdate(sql, columnIndexes);
    }

    public int executeUpdate(String sql, String columnNames[]) throws SQLException {
        return stmt.executeUpdate(sql, columnNames);
    }

    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        return stmt.execute(sql, autoGeneratedKeys);
    }

    public boolean execute(String sql, int columnIndexes[]) throws SQLException {
        return stmt.execute(sql, columnIndexes);
    }

    public boolean execute(String sql, String columnNames[]) throws SQLException {
        return stmt.execute(sql, columnNames);
    }

    public int getResultSetHoldability() throws SQLException {
        return stmt.getResultSetHoldability();
    }

    public <T> T unwrap(Class<T> iface) throws SQLException {
        return stmt.unwrap(iface);
    }

    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return stmt.isWrapperFor(iface);
    }

    public boolean isClosed() throws SQLException {
        return stmt.isClosed();
    }

    public void setPoolable(boolean poolable) throws SQLException {
        stmt.setPoolable(poolable);
    }

    public boolean isPoolable() throws SQLException {
        return stmt.isPoolable();
    }

    public void closeOnCompletion() throws SQLException {
        stmt.closeOnCompletion();
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        return stmt.isCloseOnCompletion();
    }
}
