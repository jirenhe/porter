/*
 * Copyright ©2018 vbill.cn.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package cn.vbill.middleware.porter.plugin.loader.jdbc;

import cn.vbill.middleware.porter.common.exception.TaskStopTriggerException;
import cn.vbill.middleware.porter.common.dic.LoaderPlugin;
import cn.vbill.middleware.porter.core.loader.SubmitStatObject;
import cn.vbill.middleware.porter.core.event.etl.ETLBucket;
import cn.vbill.middleware.porter.core.event.etl.ETLRow;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author: zhangkewei[zhang_kw@suixingpay.com]
 * @date: 2018年02月04日 11:38
 * @version: V1.0
 * @review: zhangkewei[zhang_kw@suixingpay.com]/2018年02月04日 11:38
 */
public class JdbcBatchLoader extends BaseJdbcLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcBatchLoader.class);

    @Override
    protected String getPluginName() {
        return LoaderPlugin.JDBC_BATCH.getCode();
    }

    @Override
    public Pair<Boolean, List<SubmitStatObject>> doLoad(ETLBucket bucket) throws TaskStopTriggerException {
        LOGGER.info("start loading bucket:{},size:{}", bucket.getSequence(), bucket.getRows().size());
        List<SubmitStatObject> affectRow = new ArrayList<>();
        for (List<ETLRow> rows : bucket.getBatchRows()) {
            if (rows.size() == 1) {
                ETLRow row = rows.get(0);
                //更新目标仓储
                int affect = loadSql(buildSql(row), row.getFinalOpType());

                //插入影响行数
                affectRow.add(new SubmitStatObject(row.getFinalSchema(), row.getFinalTable(), row.getFinalOpType(),
                        affect, row.getPosition(), row.getOpTime()));
            } else if (rows.size() > 1) { //仅支持单条记录生成一个sql的情况
                List<Pair<String, Object[]>> subList = new ArrayList<>();

                //生成sql
                for (int i = 0; i < rows.size(); i++) {
                    List<Pair<String, Object[]>> tmpSql = buildSql(rows.get(i));
                    subList.add(tmpSql.get(0));
                }

                //执行sql
                int[] results = batchLoadSql(subList, rows.get(0).getFinalOpType());


                //处理状态变更
                for (int rindex = 0; rindex < rows.size(); rindex++) {
                    int affect = rindex < results.length ? results[rindex] : 0;
                    ETLRow row = rows.get(rindex);
                    affectRow.add(new SubmitStatObject(row.getFinalSchema(), row.getFinalTable(), row.getFinalOpType(),
                            affect, row.getPosition(), row.getOpTime()));
                }
            }
        }
        if (null != bucket && null != bucket.getRows() && !bucket.getRows().isEmpty()) {
            printTimeTaken(bucket.getRows().get(0));
        }
        return new ImmutablePair(Boolean.TRUE, affectRow);
    }
}
