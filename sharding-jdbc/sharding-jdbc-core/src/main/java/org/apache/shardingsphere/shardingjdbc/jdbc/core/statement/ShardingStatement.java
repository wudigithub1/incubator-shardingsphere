/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.shardingjdbc.jdbc.core.statement;

import com.google.common.base.Optional;
import lombok.Getter;
import org.apache.shardingsphere.core.constant.properties.ShardingPropertiesConstant;
import org.apache.shardingsphere.core.executor.sql.execute.result.StreamQueryResult;
import org.apache.shardingsphere.core.merger.MergeEngine;
import org.apache.shardingsphere.core.merger.MergeEngineFactory;
import org.apache.shardingsphere.core.merger.QueryResult;
import org.apache.shardingsphere.core.optimizer.GeneratedKey;
import org.apache.shardingsphere.core.parsing.parser.sql.dal.DALStatement;
import org.apache.shardingsphere.core.parsing.parser.sql.dml.insert.InsertStatement;
import org.apache.shardingsphere.core.parsing.parser.sql.dql.DQLStatement;
import org.apache.shardingsphere.core.parsing.parser.sql.dql.select.SelectStatement;
import org.apache.shardingsphere.core.routing.SQLRouteResult;
import org.apache.shardingsphere.core.routing.StatementRoutingEngine;
import org.apache.shardingsphere.core.rule.ShardingRule;
import org.apache.shardingsphere.shardingjdbc.executor.StatementExecutor;
import org.apache.shardingsphere.shardingjdbc.jdbc.adapter.AbstractStatementAdapter;
import org.apache.shardingsphere.shardingjdbc.jdbc.core.ShardingContext;
import org.apache.shardingsphere.shardingjdbc.jdbc.core.connection.ShardingConnection;
import org.apache.shardingsphere.shardingjdbc.jdbc.core.resultset.GeneratedKeysResultSet;
import org.apache.shardingsphere.shardingjdbc.jdbc.core.resultset.ShardingResultSet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Statement that support sharding.
 *
 * @author gaohongtao
 * @author caohao
 * @author zhangliang
 * @author zhaojun
 * @author panjuan
 */
public final class ShardingStatement extends AbstractStatementAdapter {
    
    @Getter
    private final ShardingConnection connection;
    
    private final StatementExecutor statementExecutor;
    
    private boolean returnGeneratedKeys;
    
    private SQLRouteResult routeResult;
    
    private ResultSet currentResultSet;
    
    public ShardingStatement(final ShardingConnection connection) {
        this(connection, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.HOLD_CURSORS_OVER_COMMIT);
    }
    
    public ShardingStatement(final ShardingConnection connection, final int resultSetType, final int resultSetConcurrency) {
        this(connection, resultSetType, resultSetConcurrency, ResultSet.HOLD_CURSORS_OVER_COMMIT);
    }
    
    public ShardingStatement(final ShardingConnection connection, final int resultSetType, final int resultSetConcurrency, final int resultSetHoldability) {
        super(Statement.class);
        this.connection = connection;
        statementExecutor = new StatementExecutor(resultSetType, resultSetConcurrency, resultSetHoldability, connection);
    }
    
    @Override
    public ResultSet executeQuery(final String sql) throws SQLException {
        ResultSet result;
        try {
            clearPrevious();
            sqlRoute(sql);
            initStatementExecutor();
            MergeEngine mergeEngine = MergeEngineFactory.newInstance(connection.getShardingContext().getDatabaseType(), connection.getShardingContext().getShardingRule(), 
                    routeResult.getSqlStatement(), connection.getShardingContext().getMetaData().getTable(), statementExecutor.executeQuery());
            result = getResultSet(mergeEngine);
        } finally {
            currentResultSet = null;
        }
        currentResultSet = result;
        return result;
    }
    
    @Override
    public ResultSet getResultSet() throws SQLException {
        if (null != currentResultSet) {
            return currentResultSet;
        }
        if (1 == statementExecutor.getStatements().size() && routeResult.getSqlStatement() instanceof DQLStatement) {
            currentResultSet = statementExecutor.getStatements().iterator().next().getResultSet();
            return currentResultSet;
        }
        List<ResultSet> resultSets = new ArrayList<>(statementExecutor.getStatements().size());
        List<QueryResult> queryResults = new ArrayList<>(statementExecutor.getStatements().size());
        for (Statement each : statementExecutor.getStatements()) {
            ResultSet resultSet = each.getResultSet();
            resultSets.add(resultSet);
            ShardingRule shardingRule = connection.getShardingContext().getShardingRule();
            queryResults.add(new StreamQueryResult(resultSet, shardingRule.getAllActualTableNames(), shardingRule.getShardingEncryptorEngine()));
        }
        if (routeResult.getSqlStatement() instanceof SelectStatement || routeResult.getSqlStatement() instanceof DALStatement) {
            MergeEngine mergeEngine = MergeEngineFactory.newInstance(connection.getShardingContext().getDatabaseType(),
                    connection.getShardingContext().getShardingRule(), routeResult.getSqlStatement(), connection.getShardingContext().getMetaData().getTable(), queryResults);
            currentResultSet = getCurrentResultSet(resultSets, mergeEngine);
        }
        return currentResultSet;
    }
    
    private ShardingResultSet getResultSet(final MergeEngine mergeEngine) throws SQLException {
        return getCurrentResultSet(statementExecutor.getResultSets(), mergeEngine);
    }
    
    private ShardingResultSet getCurrentResultSet(final List<ResultSet> resultSets, final MergeEngine mergeEngine) throws SQLException {
        return new ShardingResultSet(resultSets, mergeEngine.merge(), this);
    }
    
    @Override
    public int executeUpdate(final String sql) throws SQLException {
        try {
            clearPrevious();
            sqlRoute(sql);
            initStatementExecutor();
            return statementExecutor.executeUpdate();
        } finally {
            currentResultSet = null;
        }
    }
    
    @Override
    public int executeUpdate(final String sql, final int autoGeneratedKeys) throws SQLException {
        if (RETURN_GENERATED_KEYS == autoGeneratedKeys) {
            returnGeneratedKeys = true;
        }
        try {
            clearPrevious();
            sqlRoute(sql);
            initStatementExecutor();
            return statementExecutor.executeUpdate(autoGeneratedKeys);
        } finally {
            currentResultSet = null;
        }
    }
    
    @Override
    public int executeUpdate(final String sql, final int[] columnIndexes) throws SQLException {
        returnGeneratedKeys = true;
        try {
            clearPrevious();
            sqlRoute(sql);
            initStatementExecutor();
            return statementExecutor.executeUpdate(columnIndexes);
        } finally {
            currentResultSet = null;
        }
    }
    
    @Override
    public int executeUpdate(final String sql, final String[] columnNames) throws SQLException {
        returnGeneratedKeys = true;
        try {
            clearPrevious();
            sqlRoute(sql);
            initStatementExecutor();
            return statementExecutor.executeUpdate(columnNames);
        } finally {
            currentResultSet = null;
        }
    }
    
    @Override
    public boolean execute(final String sql) throws SQLException {
        try {
            clearPrevious();
            sqlRoute(sql);
            initStatementExecutor();
            return statementExecutor.execute();
        } finally {
            currentResultSet = null;
        }
    }
    
    @Override
    public boolean execute(final String sql, final int autoGeneratedKeys) throws SQLException {
        if (RETURN_GENERATED_KEYS == autoGeneratedKeys) {
            returnGeneratedKeys = true;
        }
        try {
            clearPrevious();
            sqlRoute(sql);
            initStatementExecutor();
            return statementExecutor.execute(autoGeneratedKeys);
        } finally {
            currentResultSet = null;
        }
    }
    
    @Override
    public boolean execute(final String sql, final int[] columnIndexes) throws SQLException {
        returnGeneratedKeys = true;
        try {
            clearPrevious();
            sqlRoute(sql);
            initStatementExecutor();
            return statementExecutor.execute(columnIndexes);
        } finally {
            currentResultSet = null;
        }
    }
    
    @Override
    public boolean execute(final String sql, final String[] columnNames) throws SQLException {
        returnGeneratedKeys = true;
        try {
            clearPrevious();
            sqlRoute(sql);
            initStatementExecutor();
            return statementExecutor.execute(columnNames);
        } finally {
            currentResultSet = null;
        }
    }
    
    private void initStatementExecutor() throws SQLException {
        statementExecutor.init(routeResult);
        replayMethodForStatements();
    }
    
    private void replayMethodForStatements() {
        for (Statement each : statementExecutor.getStatements()) {
            replayMethodsInvocation(each);
        }
    }
    
    private void sqlRoute(final String sql) {
        ShardingContext shardingContext = connection.getShardingContext();
        routeResult = new StatementRoutingEngine(shardingContext.getShardingRule(), shardingContext.getMetaData(), shardingContext.getDatabaseType(), 
                shardingContext.getParsingResultCache(), shardingContext.getShardingProperties().<Boolean>getValue(ShardingPropertiesConstant.SQL_SHOW)).route(sql);
    }
    
    private void clearPrevious() throws SQLException {
        statementExecutor.clear();
    }
    
    @SuppressWarnings("MagicConstant")
    @Override
    public int getResultSetType() {
        return statementExecutor.getResultSetType();
    }
    
    @SuppressWarnings("MagicConstant")
    @Override
    public int getResultSetConcurrency() {
        return statementExecutor.getResultSetConcurrency();
    }
    
    @Override
    public int getResultSetHoldability() {
        return statementExecutor.getResultSetHoldability();
    }
    
    @Override
    public boolean isAccumulate() {
        return !connection.getShardingContext().getShardingRule().isAllBroadcastTables(routeResult.getSqlStatement().getTables().getTableNames());
    }
    
    @Override
    public Collection<Statement> getRoutedStatements() {
        return statementExecutor.getStatements();
    }
    
    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        Optional<GeneratedKey> generatedKey = getGeneratedKey();
        if (returnGeneratedKeys && generatedKey.isPresent()) {
            return new GeneratedKeysResultSet(routeResult.getGeneratedKey().getGeneratedKeys().iterator(), generatedKey.get().getColumnName(), this);
        }
        if (1 == getRoutedStatements().size()) {
            return getRoutedStatements().iterator().next().getGeneratedKeys();
        }
        return new GeneratedKeysResultSet();
    }
    
    private Optional<GeneratedKey> getGeneratedKey() {
        if (null != routeResult && routeResult.getSqlStatement() instanceof InsertStatement) {
            return Optional.fromNullable(routeResult.getGeneratedKey());
        }
        return Optional.absent();
    }
}
