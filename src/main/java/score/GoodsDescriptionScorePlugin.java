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
import java.util.List;
import java.util.Map;

public class GoodsDescriptionScorePlugin extends Plugin implements ScriptPlugin {
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
            if ("match_sum".equals(scriptSource)) {
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
            final int length;
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
                length = (Integer) vars.get("length");
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
//                            List<String> oriContent = (List<String>)leafLookup.doc().get(oriFieldName).getValues();
//                            int oriLen = oriContent.get(0).length();
                            int oriLen = 0;
                            List<String> segContent = (List<String>)leafLookup.doc().get(fieldname).getValues();
                            for(String tmp : segContent){
                                oriLen += tmp.length();
                            }
                            /**
                             * 获取document中字段内容
                             */
                            IndexField indexField = leafLookup.indexLookup().get(fieldname);
                            int numDocs = leafLookup.indexLookup().numDocs();
                            for(String query : terms){
                                double idf = 0;
                                try {
                                    idf = indexField.get(query).df();
                                    if (idf<=0)
                                        idf = 1;
                                    idf = Math.log(numDocs / idf) * indexField.get(query).tf();
                                } catch (Exception e) {
                                    idf = 1;
                                    e.printStackTrace();
                                }
                                values += idf * length  / (Math.abs(length - oriLen) + 1);

                            }
//                            String[] fieldTerms = leafLookup.doc().get(fieldname).getValues().toArray(new String[0]);
//                            for (String str : fieldTerms){
//                                for (String query : terms){
//                                    if (str.equals(query)){
//                                        values += 1;
//                                    }
//                                }
//                            }
                            return values;
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