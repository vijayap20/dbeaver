/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2023 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.ext.clickhouse.edit;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.ext.clickhouse.model.ClickhouseView;
import org.jkiss.dbeaver.ext.generic.GenericConstants;
import org.jkiss.dbeaver.ext.generic.edit.GenericViewManager;
import org.jkiss.dbeaver.ext.generic.model.GenericStructContainer;
import org.jkiss.dbeaver.ext.generic.model.GenericTableBase;
import org.jkiss.dbeaver.ext.generic.model.GenericView;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.edit.DBECommandContext;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.impl.edit.SQLDatabasePersistAction;
import org.jkiss.dbeaver.model.impl.sql.edit.SQLObjectEditor;
import org.jkiss.dbeaver.model.impl.sql.edit.struct.SQLTableManager;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;

import java.util.List;
import java.util.Map;

/**
 * Clickhouse table manager
 */
public class ClickhouseViewManager extends GenericViewManager {

    @Override
    protected String getDropViewType(GenericTableBase object) {
        return "TABLE";
    }
    
    @NotNull
    @Override
    protected GenericTableBase createDatabaseObject(
        @NotNull DBRProgressMonitor monitor,
        @Nullable DBECommandContext context,
        @NotNull Object container,
        @Nullable Object copyFrom, 
        @Nullable Map<String, Object> options
    ) {
        GenericStructContainer structContainer = (GenericStructContainer) container;
        String tableName = getNewChildName(monitor, structContainer, SQLTableManager.BASE_VIEW_NAME);
        GenericTableBase viewImpl = structContainer.getDataSource().getMetaModel()
            .createTableOrViewImpl(structContainer, tableName, GenericConstants.TABLE_TYPE_VIEW, null);
        if (viewImpl instanceof GenericView) {
            ((GenericView) viewImpl).setObjectDefinitionText(
                "CREATE OR REPLACE VIEW " + viewImpl.getFullyQualifiedName(DBPEvaluationContext.DDL) + " AS SELECT 1 as A\n");
        }
        return viewImpl;
    }

    @Override
    protected void addObjectModifyActions(
        @Nullable DBRProgressMonitor monitor,
        @Nullable DBCExecutionContext executionContext,
        @NotNull List<DBEPersistAction> actionList, 
        @NotNull SQLObjectEditor<GenericTableBase, GenericStructContainer>.ObjectChangeCommand command,
        @Nullable Map<String, Object> options
    ) {
        final ClickhouseView view = (ClickhouseView) command.getObject();
        String sql = view.getDDL();
        if (sql.contains("CREATE") && !sql.contains("CREATE OR REPLACE")) {
            sql = sql.replaceFirst("CREATE", "CREATE OR REPLACE");
        }
        actionList.add(new SQLDatabasePersistAction("Create view", sql));
    }
}
