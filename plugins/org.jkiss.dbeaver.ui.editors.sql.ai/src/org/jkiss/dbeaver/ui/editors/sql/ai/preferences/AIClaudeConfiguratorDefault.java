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
package org.jkiss.dbeaver.ui.editors.sql.ai.preferences;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.*;
import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.model.ai.AICompletionConstants;
import org.jkiss.dbeaver.model.ai.AIEngineSettings;
import org.jkiss.dbeaver.model.ai.AISettings;
import org.jkiss.dbeaver.model.ai.claude2.ClaudeModel;
import org.jkiss.dbeaver.model.ai.gpt3.GPTCompletionEngine;
import org.jkiss.dbeaver.model.ai.gpt3.GPTConstants;
import org.jkiss.dbeaver.model.preferences.DBPPreferenceStore;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.ui.IObjectPropertyConfigurator;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.editors.sql.ai.internal.AIUIMessages;
import org.jkiss.utils.CommonUtils;

import java.util.Locale;

public class AIClaudeConfiguratorDefault implements IObjectPropertyConfigurator<GPTCompletionEngine, AISettings> {

    private static final String API_KEY_URL = "https://platform.openai.com/account/api-keys";
    
    private static final String CLAUDE_API_KEY_URL = "https://docs.anthropic.com/claude/reference/complete_post";

    private Button enableAICheck;

    private Button includeSourceTextInCommentCheck;
//    private Text maxCompletionChoicesText;
    private Button executeQueryImmediatelyCheck;

    private Text tokenText;

    private Combo modelCombo;
    private Text claudeTemperatureText;
    private Text topPText;
    private Text topKText;
    private Button logQueryCheck;

    @Override
    public void createControl(@NotNull Composite placeholder, GPTCompletionEngine object, @NotNull Runnable propertyChangeListener) {
        enableAICheck = UIUtils.createCheckbox(
            placeholder,
            "Enable smart completion",
            "Enable AI smart completion. If you don't want to see it in SQL editor then you can disable this feature.",
            false,
            2);

        {
            Group authorizationGroup = UIUtils.createControlGroup(placeholder,
                AIUIMessages.gpt_preference_page_group_authorization,
                2,
                SWT.NONE,
                5
            );
            tokenText = UIUtils.createLabelText(authorizationGroup, AIUIMessages.gpt_preference_page_selector_token,
                "", SWT.BORDER | SWT.PASSWORD);
            tokenText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
            Link link = UIUtils.createLink(
                authorizationGroup,
                "Copy-paste API token from <a>" + CLAUDE_API_KEY_URL + "</a>",
                new SelectionAdapter() {
                    @Override
                    public void widgetSelected(SelectionEvent e) {
                        UIUtils.openWebBrowser(CLAUDE_API_KEY_URL);
                    }
                });
            GridData gd = new GridData(GridData.FILL_HORIZONTAL);
            gd.horizontalSpan = 2;
            link.setLayoutData(gd);
            authorizationGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        }
        {
            Composite settingsPanel = UIUtils.createComposite(placeholder, 2);
            settingsPanel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

            Group completionGroup = UIUtils.createControlGroup(settingsPanel,
                "Completion",
                2,
                SWT.NONE,
                5
            );
            completionGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
            includeSourceTextInCommentCheck = UIUtils.createCheckbox(
                completionGroup,
                "Include source in query comment",
                "Add your human language text in query comment",
                false,
                2);
            executeQueryImmediatelyCheck = UIUtils.createCheckbox(
                completionGroup,
                "Execute SQL immediately",
                "Try to execute translated SQL immediately after completion",
                false,
                2);

            createCompletionSettings(completionGroup, propertyChangeListener);

            createFormattingSettings(settingsPanel, propertyChangeListener);
        }
        {
            Group modelGroup = UIUtils.createControlGroup(placeholder,
                AIUIMessages.gpt_preference_page_group_model,
                2,
                SWT.NONE,
                5
            );
            modelGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
            modelCombo = UIUtils.createLabelCombo(modelGroup,
                AIUIMessages.claude_preference_page_combo_engine,
                SWT.READ_ONLY
            );
            for (ClaudeModel model : ClaudeModel.values()) {
                modelCombo.add(model.getName());
            }
//            UIUtils.createInfoLabel(modelGroup, "gpt-3.5-turbo-16k model suits the best for SQL code completion",
//                GridData.FILL_HORIZONTAL, 2);
            {
                Group modelAdvancedGroup = UIUtils.createControlGroup(placeholder,
                    AIUIMessages.gpt_preference_page_group_model_advanced,
                    2,
                    SWT.NONE,
                    5
                );

                claudeTemperatureText = UIUtils.createLabelText(modelAdvancedGroup,
                    AIUIMessages.claude_preference_page_text_temperature,
                    "0.0"
                );
                claudeTemperatureText.addVerifyListener(UIUtils.getNumberVerifyListener(Locale.getDefault()));
                UIUtils.createInfoLabel(modelAdvancedGroup, "Lower temperatures give more precise results", GridData.FILL_HORIZONTAL, 2);

                claudeTemperatureText.addVerifyListener(UIUtils.getNumberVerifyListener(Locale.getDefault()));
                modelAdvancedGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
                
                topPText = UIUtils.createLabelText(modelAdvancedGroup,
                        AIUIMessages.claude_preference_page_text_top_p,
                        "0.0"
                    );
                topPText.addVerifyListener(UIUtils.getNumberVerifyListener(Locale.getDefault()));
                UIUtils.createInfoLabel(modelAdvancedGroup, "Use nucleus Sampling", GridData.FILL_HORIZONTAL, 2);

                topPText.addVerifyListener(UIUtils.getNumberVerifyListener(Locale.getDefault()));
                modelAdvancedGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
                
                topKText = UIUtils.createLabelText(modelAdvancedGroup,
                        AIUIMessages.claude_preference_page_text_top_k,
                        "0.0"
                    );
                topKText.addVerifyListener(UIUtils.getNumberVerifyListener(Locale.getDefault()));
                UIUtils.createInfoLabel(modelAdvancedGroup, "Used to remove \"long tail\" low probability responses", GridData.FILL_HORIZONTAL, 2);

                topKText.addVerifyListener(UIUtils.getNumberVerifyListener(Locale.getDefault()));
                modelAdvancedGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
                

                logQueryCheck = UIUtils.createCheckbox(
                    modelAdvancedGroup,
                    "Write Claude queries to debug log",
                    "Write Claude queries with metadata info in debug logs",
                    false,
                    2);

                createAdvancedSettings(modelAdvancedGroup, propertyChangeListener);
            }
        }
    }

    protected void createCompletionSettings(Group group, Runnable propertyChangeListener) {

    }

    protected void createFormattingSettings(Composite settingsPanel, Runnable propertyChangeListener) {
        UIUtils.createEmptyLabel(settingsPanel, 1, 1);

    }

    protected void createAdvancedSettings(Group group, Runnable propertyChangeListener) {

    }

    @Override
    public void loadSettings(@NotNull AISettings aiSettings) {
        DBPPreferenceStore store = DBWorkbench.getPlatform().getPreferenceStore();

        AISettings settings = AISettings.getSettings();
        AIEngineSettings openAiSettings = settings.getEngineConfiguration(GPTConstants.CLAUDEAI_ENGINE);

        enableAICheck.setSelection(!settings.isAiDisabled());

        includeSourceTextInCommentCheck.setSelection(store.getBoolean(AICompletionConstants.AI_INCLUDE_SOURCE_TEXT_IN_QUERY_COMMENT));
//        maxCompletionChoicesText.setText(store.getString(AICompletionConstants.AI_COMPLETION_MAX_CHOICES));
        executeQueryImmediatelyCheck.setSelection(store.getBoolean(AICompletionConstants.AI_COMPLETION_EXECUTE_IMMEDIATELY));

        modelCombo.select(ClaudeModel.getByName(store.getString(GPTConstants.CLAUDE_MODEL)).ordinal());
        claudeTemperatureText.setText(String.valueOf(store.getDouble(GPTConstants.CLAUDE_MODEL_TEMPERATURE)));
        topPText.setText(String.valueOf(store.getDouble(GPTConstants.CLAUDE_MODEL_TOP_P)));
        topKText.setText(String.valueOf(store.getDouble(GPTConstants.CLAUDE_MODEL_TOP_K)));
        logQueryCheck.setSelection(store.getBoolean(GPTConstants.CLAUDE_LOG_QUERY));

        String secretValue = CommonUtils.toString(openAiSettings.getProperties().get(GPTConstants.CLAUDE_API_TOKEN), null);
        if (secretValue == null) {
            secretValue = DBWorkbench.getPlatform().getPreferenceStore().getString(GPTConstants.CLAUDE_API_TOKEN);
        }
        tokenText.setText(secretValue == null ? "" : secretValue);
    }

    @Override
    public void saveSettings(@NotNull AISettings settings) {
        DBPPreferenceStore store = DBWorkbench.getPlatform().getPreferenceStore();

        settings.setAiDisabled(!enableAICheck.getSelection());

        AIEngineSettings engineConfiguration = settings.getEngineConfiguration(GPTConstants.OPENAI_ENGINE);
        engineConfiguration.setEngineEnabled(enableAICheck.getSelection());

        store.setValue(AICompletionConstants.AI_INCLUDE_SOURCE_TEXT_IN_QUERY_COMMENT, includeSourceTextInCommentCheck.getSelection());
        store.setValue(AICompletionConstants.AI_COMPLETION_EXECUTE_IMMEDIATELY, executeQueryImmediatelyCheck.getSelection());
//        store.setValue(AICompletionConstants.AI_COMPLETION_MAX_CHOICES, maxCompletionChoicesText.getText());

        store.setValue(GPTConstants.CLAUDE_MODEL, modelCombo.getText());
        store.setValue(GPTConstants.CLAUDE_MODEL_TEMPERATURE, claudeTemperatureText.getText());
        store.setValue(GPTConstants.CLAUDE_MODEL_TOP_P, topPText.getText());
        store.setValue(GPTConstants.CLAUDE_MODEL_TOP_K, topKText.getText());
        store.setValue(GPTConstants.CLAUDE_LOG_QUERY, logQueryCheck.getSelection());

        if (!modelCombo.getText().equals(store.getString(GPTConstants.CLAUDE_MODEL)) ||
            !tokenText.getText().equals(store.getString(GPTConstants.CLAUDE_API_TOKEN))
        ) {
            GPTCompletionEngine.resetServices();
        }
        store.setToDefault(GPTConstants.CLAUDE_API_TOKEN);
        engineConfiguration.getProperties().put(GPTConstants.CLAUDE_API_TOKEN, tokenText.getText());
    }

    @Override
    public void resetSettings(@NotNull AISettings settings) {

    }

    @Override
    public boolean isComplete() {
        return !CommonUtils.isEmpty(tokenText.getText());
    }

}
