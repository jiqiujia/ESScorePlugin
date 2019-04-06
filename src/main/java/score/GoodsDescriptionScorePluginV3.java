package score;

import org.apache.lucene.index.LeafReaderContext;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.ScriptPlugin;
import org.elasticsearch.script.*;
import org.elasticsearch.search.lookup.IndexField;
import org.elasticsearch.search.lookup.LeafSearchLookup;
import org.elasticsearch.search.lookup.SearchLookup;

import java.io.IOException;
import java.util.Map;

public class GoodsDescriptionScorePluginV3 extends Plugin implements ScriptPlugin {
    @Override
    public ScriptEngineService getScriptEngineService(Settings settings) {
        return new MyExpertScriptEngine();
    }

    private static class MyExpertScriptEngine implements ScriptEngineService {
        @Override
        public String getType() {
            return "expert_scripts";
        }

        @Override
        public Object compile(String scriptName, String scriptSource, Map<String, String> params) {
            if ("match_sum_v3".equals(scriptSource)) {
                return scriptSource;
            }
            throw new IllegalArgumentException("Unknown script name " + scriptSource);
        }

        @Override
        @SuppressWarnings("unchecked")
        public SearchScript search(CompiledScript compiledScript, SearchLookup lookup, @Nullable Map<String, Object> vars) {

            /**
             * 校验输入参数，DSL中params 参数列表
             */
            final String[] terms;
            final String fieldname;
//            final String oriFieldName;
            final int lengthThres;
            final double cateScoreThres;
            if (vars == null || !vars.containsKey("terms")) {
                throw new IllegalArgumentException("Missing parameter [terms]");
            } else {
                terms = vars.get("terms").toString().split(" ");
            }

            if (!vars.containsKey("fieldname")) {
                throw new IllegalArgumentException("Missing parameter [fieldname]");
            } else {
                fieldname = (String) vars.get("fieldname");
            }

//            if (!vars.containsKey("orifieldname")) {
//                throw new IllegalArgumentException("Missing parameter [orifieldname]");
//            } else {
//                oriFieldName = (String) vars.get("orifieldname");
//            }

            if (!vars.containsKey("length")) {
                throw new IllegalArgumentException("Missing parameter [length]");
            } else {
                lengthThres = (Integer) vars.get("length");
            }

            if (!vars.containsKey("cate_score")) {
                throw new IllegalArgumentException("Missing parameter [cate_score]");
            } else {
                cateScoreThres = (Integer) vars.get("cate_score");
            }
            return new SearchScript() {

                @Override
                public LeafSearchScript getLeafSearchScript(LeafReaderContext context) throws IOException {
                    final LeafSearchLookup leafLookup = lookup.getLeafSearchLookup(context);


                    return new LeafSearchScript() {
                        @Override
                        public void setDocument(int doc) {
                            if (leafLookup != null) {
                                leafLookup.setDocument(doc);
                            }
                        }

                        @Override
                        public double runAsDouble() {
                            double values = 0;
                            Double cate_score = Double.valueOf(leafLookup.doc().get("cate_score").getValues().get(0).toString());
                            if (cate_score < cateScoreThres)
                                return 0;
                            int length = Integer.valueOf(leafLookup.doc().get("len").getValues().get(0).toString());
                            if (length < lengthThres)
                                return 0;

                            /**
                             * 获取document中字段内容
                             */
                            IndexField indexField = leafLookup.indexLookup().get(fieldname);
                            int hitNum = 0;
                            for(String query : terms){
                                try{
                                    double tf = indexField.get(query).tf();
                                    if (tf>=1) {    // 防止为NaN报错
                                        values += 1.0 / tf;
                                        hitNum += tf;
                                    }
                                }catch (Exception e){
                                    e.printStackTrace();
                                }
                            }

                            // TODO: NotSerializableExceptionWrapper: unsupported_operation_exception: size() not supported!
//                                long oriLen = indexField.sumttf() * 2;
                            //values = values * cate_score * (1 - (Math.abs(length - oriLen)/length)) - (oriLen/3 - hitNum);
                            return values * cate_score;
                        }
                    };
                }

                @Override
                public boolean needsScores() {
                    return false;
                }
            };
        }

        @Override
        public ExecutableScript executable(CompiledScript compiledScript, @Nullable Map<String, Object> params) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isInlineScriptEnabled() {
            return true;
        }

        @Override
        public void close() throws IOException {
        }
    }
}