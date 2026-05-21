package com.chatapp.common.aop;

import java.util.List;
import java.util.stream.Collectors;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.proxy.ParameterSetOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * JDBC-level listener (datasource-proxy) that logs the full SQL with actual bound values. Works in
 * tandem with SqlTraceAspect: the aspect sets MDC["sqlCaller"] before the service call, this
 * listener reads it after each statement executes.
 *
 * <p>Log format: [SQL-TRACE] MessageService.findPagedMessages | params=[42, 20] | time=3ms SELECT
 * m.id FROM messages m WHERE m.room_id=42 AND m.deleted_at IS NULL LIMIT 20
 */
public class SqlTraceQueryListener implements QueryExecutionListener {

  private static final Logger log = LoggerFactory.getLogger(SqlTraceQueryListener.class);
  private static final String MDC_KEY = "sqlCaller";

  @Override
  public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {}

  @Override
  public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
    if (!log.isDebugEnabled()) return;

    String caller = MDC.get(MDC_KEY);
    if (caller == null) return;

    long elapsedMs = execInfo.getElapsedTime();

    for (QueryInfo queryInfo : queryInfoList) {
      String sql = queryInfo.getQuery().trim();

      List<List<ParameterSetOperation>> batchParams = queryInfo.getParametersList();

      if (batchParams.size() <= 1) {
        // single execute — log flat
        List<String> params =
            batchParams.stream()
                .flatMap(List::stream)
                .map(SqlTraceQueryListener::extractValue)
                .collect(Collectors.toList());
        log.debug("[SQL-TRACE] {} | params={} | time={}ms\n  {}", caller, params, elapsedMs, sql);
      } else {
        // batch execute — log mỗi row riêng
        log.debug(
            "[SQL-TRACE] {} | batch={} rows | time={}ms\n  {}",
            caller,
            batchParams.size(),
            elapsedMs,
            sql);
        for (int i = 0; i < batchParams.size(); i++) {
          List<String> rowParams =
              batchParams.get(i).stream()
                  .map(SqlTraceQueryListener::extractValue)
                  .collect(Collectors.toList());
          log.debug("  [batch row {}] params={}", i + 1, rowParams);
        }
      }
    }
  }

  private static String extractValue(ParameterSetOperation op) {
    Object[] args = op.getArgs();
    Object value = (args != null && args.length > 1) ? args[1] : null;
    return value == null ? "NULL" : value.toString();
  }
}
