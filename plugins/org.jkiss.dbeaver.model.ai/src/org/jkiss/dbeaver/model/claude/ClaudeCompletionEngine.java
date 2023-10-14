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
package org.jkiss.dbeaver.model.ai.claude2;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.theokanning.openai.OpenAiHttpException;
import com.theokanning.openai.completion.CompletionChoice;
import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import okhttp3.ResponseBody;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.ai.AICompletionConstants;
import org.jkiss.dbeaver.model.ai.AIEngineSettings;
import org.jkiss.dbeaver.model.ai.AISettings;
import org.jkiss.dbeaver.model.ai.completion.DAICompletionEngine;
import org.jkiss.dbeaver.model.ai.completion.DAICompletionRequest;
import org.jkiss.dbeaver.model.ai.completion.DAICompletionResponse;
import org.jkiss.dbeaver.model.ai.completion.DAICompletionScope;
import org.jkiss.dbeaver.model.claude.ClaudeConnect;
import org.jkiss.dbeaver.model.claude.TextToCompletionArray;
import org.jkiss.dbeaver.model.data.json.JSONUtils;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.exec.DBCExecutionContextDefaults;
import org.jkiss.dbeaver.model.logical.DBSLogicalDataSource;
import org.jkiss.dbeaver.model.navigator.DBNUtils;
import org.jkiss.dbeaver.model.preferences.DBPPreferenceStore;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSEntity;
import org.jkiss.dbeaver.model.struct.DBSEntityAttribute;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.DBSObjectContainer;
import org.jkiss.dbeaver.model.struct.rdb.DBSSchema;
import org.jkiss.dbeaver.model.struct.rdb.DBSTablePartition;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.utils.RuntimeUtils;
import org.jkiss.utils.CommonUtils;
import retrofit2.HttpException;
import retrofit2.Response;

import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClaudeCompletionEngine implements DAICompletionEngine {
    private static final Log log = Log.getLog(ClaudeCompletionEngine.class);

    //How many retries may be done if code 429 happens
    private static final int MAX_REQUEST_ATTEMPTS = 3;

    private static final Map<String, OpenAiService> clientInstances = new HashMap<>();
    private static final int GPT_MODEL_MAX_RESPONSE_TOKENS = 2000;
    private static final boolean SUPPORTS_ATTRS = true;

    private static final Pattern sizeErrorPattern = Pattern.compile("This model's maximum context length is [0-9]+ tokens. "
        + "\\wowever[, ]+you requested [0-9]+ tokens \\(([0-9]+) in \\w+ \\w+[;,] [0-9]+ \\w+ \\w+ completion\\). "
        + "Please reduce .+");


    public ClaudeCompletionEngine() {
    }

    @Override
    public String getEngineName() {
        return "claude-2";
    }

    @Override
    public String getModelName() {
    	log.debug("Model Name : "+DBWorkbench.getPlatform().getPreferenceStore().getString(ClaudeConstants.GPT_MODEL));
        return DBWorkbench.getPlatform().getPreferenceStore().getString(ClaudeConstants.GPT_MODEL);
    }

    @NotNull
    @Override
    public List<DAICompletionResponse> performQueryCompletion(
        @NotNull DBRProgressMonitor monitor,
        @Nullable DBSLogicalDataSource dataSource,
        @NotNull DBCExecutionContext executionContext,
        @NotNull DAICompletionRequest completionRequest,
        boolean returnOnlyCompletion,
        int maxResults
    ) throws DBException {
        String result = requestCompletion(completionRequest, monitor, executionContext);
        DAICompletionResponse response = createCompletionResponse(dataSource, executionContext, result);
        return Collections.singletonList(response);
    }

    public boolean isValidConfiguration() {
        return !CommonUtils.isEmpty(acquireToken());
    }

    @NotNull
    protected DAICompletionResponse createCompletionResponse(DBSLogicalDataSource dataSource, DBCExecutionContext executionContext, String result) {
        DAICompletionResponse response = new DAICompletionResponse();
        response.setResultCompletion(result);
        return response;
    }

    /**
     * Initializes OpenAiService instance using token provided by {@link GPTConstants} GTP_TOKEN_PATH
     */
    private static OpenAiService initGPTApiClientInstance() throws DBException {
        String token = acquireToken();
        if (CommonUtils.isEmpty(token)) {
            throw new DBException("Empty API token value");
        }
        return new OpenAiService(token, Duration.ofSeconds(30));
    }

    private static String acquireToken() {
        AIEngineSettings openAiConfig = AISettings.getSettings().getEngineConfiguration(ClaudeConstants.OPENAI_ENGINE);
        Object token = openAiConfig.getProperties().get(ClaudeConstants.GPT_API_TOKEN);
        if (token != null) {
            return token.toString();
        }
        log.debug("Acquired Roken : "+DBWorkbench.getPlatform().getPreferenceStore().getString(ClaudeConstants.GPT_API_TOKEN));
        return DBWorkbench.getPlatform().getPreferenceStore().getString(ClaudeConstants.GPT_API_TOKEN);
    }

    /**
     * Request completion from GPT API uses parameters from {@link GPTConstants} for model settings\
     * Adds current schema metadata to starting query
     *
     * @param request          request text
     * @param monitor          execution monitor
     * @return resulting string
     */
    private String requestCompletion(
        @NotNull DAICompletionRequest request,
        @NotNull DBRProgressMonitor monitor,
        @NotNull DBCExecutionContext executionContext
    ) throws DBException {
        DAICompletionScope scope = request.getScope();
        DBSObjectContainer mainObject = null;
        DBCExecutionContextDefaults<?,?> contextDefaults = executionContext.getContextDefaults();
        if (contextDefaults != null) {
            switch (scope) {
                case CURRENT_SCHEMA:
                    if (contextDefaults.getDefaultSchema() != null) {
                        mainObject = contextDefaults.getDefaultSchema();
                    } else {
                        mainObject = contextDefaults.getDefaultCatalog();
                    }
                    break;
                case CURRENT_DATABASE:
                    mainObject = contextDefaults.getDefaultCatalog();
                    break;
                default:
                    break;
            }
        }
        if (mainObject == null) {
            mainObject = ((DBSObjectContainer) executionContext.getDataSource());
        }

        DBPDataSourceContainer container = executionContext.getDataSource().getContainer();
        OpenAiService service = clientInstances.get(container.getId());
        if (service == null) {
            service = initGPTApiClientInstance();
            clientInstances.put(container.getId(), service);
        }
        String modifiedRequest = addDBMetadataToClaudeRequest(monitor, request, executionContext, mainObject,
            ClaudeModel.getByName(getModelName()));
        if (monitor.isCanceled()) {
            return "";
        }

        Object completionRequest = createCompletionRequest(modifiedRequest);
        monitor.subTask("Request GPT completion");
        try {
            if (DBWorkbench.getPlatform().getPreferenceStore().getBoolean(ClaudeConstants.GPT_LOG_QUERY)) {
                if (completionRequest instanceof ChatCompletionRequest) {
                    log.debug("Chat GPT request:\n" + ((ChatCompletionRequest) completionRequest).getMessages().get(0).getContent());
                } else {
                    log.debug("GPT request:\n" + ((CompletionRequest) completionRequest).getPrompt());
                }
            }
            if (monitor.isCanceled()) {
                return null;
            }

            try {
//                List<?> choices;
//                int responseSize = GPT_MODEL_MAX_RESPONSE_TOKENS;
//                for (int i = 0; ; i++) {
//                    try {
//                        choices = getCompletionChoices(service, completionRequest);
//                        break;
//                    } catch (Exception e) {
//                        if ((e instanceof HttpException && ((HttpException) e).code() == 429)
//                            || (e instanceof OpenAiHttpException && e.getMessage().contains("This model's maximum"))) {
//                            if (e instanceof HttpException) {
//                                RuntimeUtils.pause(1000);
//                            } else {
//                                // Extracts resulted prompt size from the error message and resizes max response to
//                                // value lower that (maxTokens - prompt size)
//                                Matcher matcher = sizeErrorPattern.matcher(e.getMessage());
//                                int promptSize;
//                                if (matcher.find()) {
//                                    String numberStr = matcher.group(1);
//                                    promptSize = CommonUtils.toInt(numberStr);
//                                } else {
//                                    throw e;
//                                }
//                                responseSize = Math.min(responseSize,
//                                    GPTModel.getByName(getModelName()).getMaxTokens() - promptSize - 1);
//                                if (responseSize < 0) {
//                                    throw e;
//                                }
//                                completionRequest = createCompletionRequest(modifiedRequest, responseSize);
//                            }
//                            if (i >= MAX_REQUEST_ATTEMPTS - 1) {
//                                throw e;
//                            } else {
//                                if (e instanceof HttpException) {
//                                    log.debug("AI service failed. Retry (" + e.getMessage() + ")");
//                                }
//                                continue;
//                            }
//                        }
//                        throw e;
//                    }
//                }
//                String completionText;
//                Object choice = choices.stream().findFirst().orElseThrow();
//                if (choice instanceof CompletionChoice) {
//                    completionText = ((CompletionChoice) choice).getText();
//                } else {
//                    completionText = ((ChatCompletionChoice) choice).getMessage().getContent();
//                }
//                if (CommonUtils.isEmpty(completionText)) {
//                    return null;
//                }
//                completionText = "SELECT " + completionText.trim() + ";";
//                completionText = postProcessGeneratedQuery(monitor, mainObject, executionContext, completionText);
//                if (DBWorkbench.getPlatform().getPreferenceStore().getBoolean(AICompletionConstants.AI_INCLUDE_SOURCE_TEXT_IN_QUERY_COMMENT)) {
//                    String[] lines = request.getPromptText().split("\n");
//                    for (String line : lines) {
//                        if (!CommonUtils.isEmpty(line)) {
//                            completionText = "-- " + line.trim() + "\n" + completionText;
//                        }
//                    }
//                }
            	String completionText, claudePrompt = null;
            	log.debug("Modified Prompt : "+modifiedRequest);
                ClaudeConnect claude = new ClaudeConnect(modifiedRequest);
                completionText = claude.parseResponseBody();
		log.debug("Claude Response Text : "+completionText);
		String tempString = extractSQLQueryFromCompletionResponse(completionText);
		log.debug("Claude Response Text(Final) : "+tempString);
                //String[] _responseArr = completionText.split("\\r\\n\\r\\n");
                //String respContent = _responseArr[_responseArr.length-2];
                //respContent = processCompletionText(respContent);
                //completionText = respContent ; 
                //HashSet<String> responseContent = new HashSet<String>();
                //String tempString = null ; 
                //tempString = sqlQueryExtractor(respContent);
                //System.out.println("TempSTring " +tempString);
//                for(String respContent : _responseArr) {
//                	tempString = sqlQueryExtractor(respContent);
//                	System.out.println("String Value : "+tempString);
////                	System.out.println(respContent);
////                	TextToCompletionArray ttca = new TextToCompletionArray();
////                	System.out.println(processCompletionText(respContent));
//////                	jsonDataExtractor(respContent);
////                	//System.out.println("Resp Content:"+respContent+"\nTemp String : "+tempString);
////                	
////                	responseContent.add(tempString);
//                }
                System.out.println(responseContent);
//                completionText = _responseArr[_responseArr.length-4];
//                System.out.println("Completion Text : "+completionText);
//                completionText = sqlQueryExtractor(completionText);
                return tempString.trim();
            } catch (Exception exception) {
                if (exception instanceof HttpException) {
                    Response<?> response = ((HttpException) exception).response();
                    if (response != null) {
                        try {
                            try (ResponseBody responseBody = response.errorBody()) {
                                if (responseBody != null) {
                                    String bodyString = responseBody.string();
                                    if (!CommonUtils.isEmpty(bodyString)) {
                                        try {
                                            Gson gson = new Gson();
                                            Map<String, Object> map = JSONUtils.parseMap(gson, new StringReader(bodyString));
                                            Map<String, Object> error = JSONUtils.deserializeProperties(map, "error");
                                            if (error != null) {
                                                String message = JSONUtils.getString(error, "message");
                                                if (!CommonUtils.isEmpty(message)) {
                                                    bodyString = message;
                                                }
                                            }
                                        } catch (Exception e) {
                                            // ignore json errors
                                        }
                                        throw new DBException("AI service error: " + bodyString);
                                    }
                                }
                            }
                        } catch (IOException e) {
                            log.debug(e);
                        }
                    }
                }
                throw exception;
            }

        } finally {
            monitor.done();
        }
    }
    
    private void jsonDataExtractor(String data) {
    	
    }

    private String extractSQLQueryFromCompletionResponse(String response) {
    	int startIndex = response.indexOf("<sql>");
    	int endIndex = response.indexOf("</sql>");    	  
    	if(startIndex == -1 || endIndex == -1) {
    	   return ""; 
    	}    	  
    	String sql = response.substring(startIndex + 6, endIndex);
    	return sql.trim();
    }
    
    private String processCompletionText(String text){
      ArrayList<String> completions = new ArrayList<>();
      Pattern pattern = Pattern.compile("\"completion\":\"(.*?)\"");
      Matcher matcher = pattern.matcher(text);

      while (matcher.find()) {
          completions.add(matcher.group(1));
      }

      String[] completionArray = completions.toArray(new String[0]);

      // Print the extracted completions
      for (String completion : completionArray) {
          System.out.println(completion);
      }
      System.out.println(completionArray[completionArray.length-1]);
      String responseData = null ;
      if(completionArray.length > 0) {
    	 responseData = completionArray[completionArray.length-1];
      }
      return responseData;
  }
    
    private String sqlQueryExtractor(String claudeResponse) {
    	String startTag = "<sql>";
        String endTag = "</sql>";
//        String startHash = "###";
//        String endHash = "###";
        String extractedString = null ; 
        int startIndex = -1, endIndex = -1;
        //claudeResponse = convertToJavaStringLiteral(claudeResponse);
        System.out.println("Claude Response : "+claudeResponse);
        if(claudeResponse.contains(startTag)) {
        	startIndex = claudeResponse.indexOf(startTag);
        	endIndex = claudeResponse.indexOf(endTag, startIndex + startTag.length());
		} /*
			 * else if (claudeResponse.contains(startHash)) { startIndex =
			 * claudeResponse.indexOf(startHash); endIndex = claudeResponse.indexOf(endHash,
			 * startIndex + startHash.length()); }
			 */       
        if (startIndex != -1 && endIndex != -1) {
            extractedString = claudeResponse.substring(startIndex + startTag.length(), endIndex);
        } else {
        	extractedString = "NoData";
        }
        System.out.println("Extracted String  : "+extractedString);
    	return extractedString;
    }
    
    public static String convertToJavaStringLiteral(String input) {
        StringBuilder result = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (c == '\n') {
                result.append("\\n");
            } else if (c == '\t') {
                result.append("\\t");
            } else if (c == '\b') {
                result.append("\\b");
            } else if (c == '\r') {
                result.append("\\r");
            } else if (c == '\f') {
                result.append("\\f");
            } else if (c == '\\') {
                result.append("\\\\");
            } else if (c == '\'') {
                result.append("\\'");
            } else if (c == '\"') {
                result.append("\\\"");
            } else if (c >= 32 && c <= 126) {
                result.append(c);
            } else {
                result.append(String.format("\\u%04x", (int) c));
            }
        }
        return "\"" + result.toString() + "\"";
    }

	private List<?> getCompletionChoices(OpenAiService service, Object completionRequest) {
    	if (completionRequest instanceof CompletionRequest) { 
    		return service.createCompletion((CompletionRequest) completionRequest).getChoices();
		} else {
			return service.createChatCompletion((ChatCompletionRequest) completionRequest).getChoices(); 
		}   	
    }

    private static DBPPreferenceStore getPreferenceStore() {
        return DBWorkbench.getPlatform().getPreferenceStore();
    }

    private static Object createCompletionRequest(@NotNull String request) throws DBException {
        return createCompletionRequest(request, GPT_MODEL_MAX_RESPONSE_TOKENS);
    }

    private static Object createCompletionRequest(@NotNull String request, int responseSize) {
        Double temperature = getPreferenceStore().getDouble(ClaudeConstants.GPT_MODEL_TEMPERATURE);
        String modelId = getPreferenceStore().getString(ClaudeConstants.GPT_MODEL);
        log.debug("Model ID : " + modelId);
        ClaudeModel model = CommonUtils.isEmpty(modelId) ? null : ClaudeModel.getByName(modelId);
        if (model == null) {
            model = ClaudeModel.GPT_TURBO16;
        }
        if (model.isChatAPI()) {
        	log.debug("<GPTCompletionEngine> It is Chat API....");
            return buildChatRequest(request, responseSize, temperature, modelId);
        } else {
            return buildLegacyAPIRequest(request, responseSize, temperature, modelId);
        }
    }

    private static CompletionRequest buildLegacyAPIRequest(
        @NotNull String request,
        int maxTokens,
        Double temperature,
        String modelId
    ) {
        CompletionRequest.CompletionRequestBuilder builder =
            CompletionRequest.builder().prompt(request);
        return builder
            .temperature(temperature)
            .maxTokens(maxTokens)
            .frequencyPenalty(0.0)
            .n(1)
            .presencePenalty(0.0)
            .stop(List.of("#", ";"))
            .model(modelId)
            //.echo(true)
            .build();
    }

    private static ChatCompletionRequest buildChatRequest(
        @NotNull String request,
        int maxTokens,
        Double temperature,
        String modelId
    ) {
        ChatMessage message = new ChatMessage("user", request);
        ChatCompletionRequest.ChatCompletionRequestBuilder builder =
            ChatCompletionRequest.builder().messages(Collections.singletonList(message));

        return builder
            .temperature(temperature)
            .maxTokens(maxTokens)
            .frequencyPenalty(0.0)
            .presencePenalty(0.0)
            .n(1)
            .stop(List.of("#", ";"))
            .model(modelId)
            //.echo(true)
            .build();
    }

    /**
     * Resets GPT client cache
     */
    public static void resetServices() {
        clientInstances.clear();
    }

    /**
     * Add completion metadata to request
     */
    protected String addDBMetadataToRequest(
        DBRProgressMonitor monitor,
        DAICompletionRequest request,
        DBCExecutionContext executionContext,
        DBSObjectContainer mainObject,
        ClaudeModel model
    ) throws DBException {
        if (mainObject == null || mainObject.getDataSource() == null || CommonUtils.isEmptyTrimmed(request.getPromptText())) {
            throw new DBException("Invalid completion request");
        }

        StringBuilder additionalMetadata = new StringBuilder();
        additionalMetadata.append("### ")
            .append(mainObject.getDataSource().getSQLDialect().getDialectName())
            .append(" SQL tables, with their properties:\n#\n");
        String tail = "";
        if (executionContext != null && executionContext.getContextDefaults() != null) {
            DBSSchema defaultSchema = executionContext.getContextDefaults().getDefaultSchema();
            if (defaultSchema != null) {
                tail += "#\n# Current schema is " + defaultSchema.getName() + "\n";
            }
        }
        int maxRequestLength = model.getMaxTokens() - additionalMetadata.length() - tail.length() - 20 - GPT_MODEL_MAX_RESPONSE_TOKENS;

        if (request.getScope() != DAICompletionScope.CUSTOM) {
            additionalMetadata.append(generateObjectDescription(
                monitor,
                request,
                mainObject,
                executionContext,
                maxRequestLength,
                false
            ));
        } else {
            for (DBSEntity entity : request.getCustomEntities()) {
                additionalMetadata.append(generateObjectDescription(
                    monitor,
                    request,
                    entity,
                    executionContext,
                    maxRequestLength,
                    isRequiresFullyQualifiedName(entity, executionContext)
                ));
            }
        }

        String promptText = request.getPromptText().trim();
        promptText = postProcessPrompt(monitor, mainObject, executionContext, promptText);
        additionalMetadata.append(tail).append("#\n###").append(promptText).append("\nSELECT");
        return additionalMetadata.toString();
    }
    
    
    /**
     * Add completion metadata to request
     */
    protected String addDBMetadataToClaudeRequest(
        DBRProgressMonitor monitor,
        DAICompletionRequest request,
        DBCExecutionContext executionContext,
        DBSObjectContainer mainObject,
        ClaudeModel model
    ) throws DBException {
        if (mainObject == null || mainObject.getDataSource() == null || CommonUtils.isEmptyTrimmed(request.getPromptText())) {
            throw new DBException("Invalid completion request");
        }
        
        String promptText = request.getPromptText().trim();

        StringBuilder additionalMetadata = new StringBuilder();
        additionalMetadata.append("Create a sql query to ")
        	.append(promptText)
        	.append(" ### ")
            .append(mainObject.getDataSource().getSQLDialect().getDialectName())
            .append(" SQL tables, with their properties:# ");
        String tail = "";
        if (executionContext != null && executionContext.getContextDefaults() != null) {
            DBSSchema defaultSchema = executionContext.getContextDefaults().getDefaultSchema();
            if (defaultSchema != null) {
                tail += "# Current schema is " + defaultSchema.getName();
            }
        }
        int maxRequestLength = model.getMaxTokens() - additionalMetadata.length() - tail.length() - 20 - GPT_MODEL_MAX_RESPONSE_TOKENS;

        if (request.getScope() != DAICompletionScope.CUSTOM) {
            additionalMetadata.append(generateObjectDescription(
                monitor,
                request,
                mainObject,
                executionContext,
                maxRequestLength,
                false
            ));
        } else {
            for (DBSEntity entity : request.getCustomEntities()) {
                additionalMetadata.append(generateObjectDescription(
                    monitor,
                    request,
                    entity,
                    executionContext,
                    maxRequestLength,
                    isRequiresFullyQualifiedName(entity, executionContext)
                ));
            }
        }

        
        promptText = postProcessPrompt(monitor, mainObject, executionContext, promptText);
        additionalMetadata.append(tail);
        additionalMetadata.append(" give the output SQL inside a tag <sql> </sql>");
        return additionalMetadata.toString();
    }

    private boolean isRequiresFullyQualifiedName(@NotNull DBSObject object, @Nullable DBCExecutionContext context) {
        if (context == null || context.getContextDefaults() == null) {
            return false;
        }
        DBSObject parent = object.getParentObject();
        DBCExecutionContextDefaults contextDefaults = context.getContextDefaults();
        return parent != null && !(parent.equals(contextDefaults.getDefaultCatalog())
            || parent.equals(contextDefaults.getDefaultSchema()));
    }

    private String generateObjectDescription(
        @NotNull DBRProgressMonitor monitor,
        @NotNull DAICompletionRequest request,
        @NotNull DBSObject object,
        @Nullable DBCExecutionContext context,
        int maxRequestLength,
        boolean useFullyQualifiedName
    ) throws DBException {
        if (DBNUtils.getNodeByObject(monitor, object, false) == null) {
            // Skip hidden objects
            return "";
        }
        StringBuilder description = new StringBuilder();
        if (object instanceof DBSEntity) {
            String name = useFullyQualifiedName && context != null ? DBUtils.getObjectFullName(
                context.getDataSource(),
                object,
                DBPEvaluationContext.DDL
            ) : DBUtils.getQuotedIdentifier(object);
            description.append("# ").append(name);
            description.append("(");
            boolean firstAttr = addPromptAttributes(monitor, (DBSEntity) object, description, true);
            addPromptExtra(monitor, (DBSEntity) object, description, firstAttr);

            description.append("); ");
        } else if (object instanceof DBSObjectContainer) {
            monitor.subTask("Load cache of " + object.getName());
            ((DBSObjectContainer) object).cacheStructure(
                monitor,
                DBSObjectContainer.STRUCT_ENTITIES | DBSObjectContainer.STRUCT_ATTRIBUTES);
            int totalChildren = 0;
            for (DBSObject child : ((DBSObjectContainer) object).getChildren(monitor)) {
                if (DBUtils.isSystemObject(child) || DBUtils.isHiddenObject(child) || child instanceof DBSTablePartition) {
                    continue;
                }
                String childText = generateObjectDescription(
                    monitor,
                    request,
                    child,
                    context,
                    maxRequestLength,
                    isRequiresFullyQualifiedName(child, context)
                );
                if (description.length() + childText.length() > maxRequestLength * 3) {
                    log.debug("Trim GPT metadata prompt  at table '" + child.getName() + "' - too long request");
                    break;
                }
                description.append(childText);
                totalChildren++;
            }
        }
        return description.toString();
    }

    protected boolean addPromptAttributes(
        DBRProgressMonitor monitor,
        DBSEntity entity,
        StringBuilder prompt,
        boolean firstAttr
    ) throws DBException {
        if (SUPPORTS_ATTRS) {
            List<? extends DBSEntityAttribute> attributes = entity.getAttributes(monitor);
            if (attributes != null) {
                for (DBSEntityAttribute attribute : attributes) {
                    if (DBUtils.isHiddenObject(attribute)) {
                        continue;
                    }
                    if (!firstAttr) prompt.append(",");
                    firstAttr = false;
                    prompt.append(attribute.getName());
                }
            }
        }
        return firstAttr;
    }

    protected void addPromptExtra(
        DBRProgressMonitor monitor,
        DBSEntity object,
        StringBuilder description,
        boolean firstAttr
    ) throws DBException {

    }

    protected String postProcessPrompt(
        DBRProgressMonitor monitor,
        DBSObjectContainer mainObject,
        DBCExecutionContext executionContext,
        String promptText
    ) {

        return promptText;
    }

    protected String postProcessGeneratedQuery(
        DBRProgressMonitor monitor,
        DBSObjectContainer mainObject,
        DBCExecutionContext executionContext,
        String completionText
    ) {

        return completionText;
    }

}
