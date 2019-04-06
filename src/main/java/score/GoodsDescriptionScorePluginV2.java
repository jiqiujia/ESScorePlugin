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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GoodsDescriptionScorePluginV2 extends Plugin implements ScriptPlugin {

    private static Set<String> customDictionary = new HashSet<>();

    @Override
    public ScriptEngineService getScriptEngineService(Settings settings) {
        return new MyExpertScriptEngine();
    }

    private static class MyExpertScriptEngine implements ScriptEngineService {
        static {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    MyExpertScriptEngine.class.getResourceAsStream("/customDictionary.txt")));
            try {
                String line = "";
                while ((line = reader.readLine()) != null) {
                    customDictionary.add(line.trim());
                }
            }catch (IOException e){
                e.printStackTrace();
            }
        }

        @Override
        public String getType() {
            return "expert_scripts";
        }

        @Override
        public Object compile(String scriptName, String scriptSource, Map<String, String> params) {
            if ("match_sum_v2".equals(scriptSource)) {
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
                            Double cate_score = Double.valueOf(leafLookup.doc().get("cate_score").getValues().get(0).toString());
                            int oriLen = 0;
                            int tagNum = 0;
                            List<String> segContent = (List<String>)leafLookup.doc().get(fieldname).getValues();
                            for(String tmp : segContent){
                                oriLen += tmp.length();
                                if (customDictionary.contains(tmp))
                                    tagNum += 1;
                            }
//                            for (String query : terms){
//                                if (segContent.contains(query)){
//                                    values += 1;
//                                }
//                            }
//                            values = values * cate_score * 100 + length / (Math.abs(length - oriLen) + 1);

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

                            values = values * cate_score * (1 - (Math.abs(length - oriLen)/length)) - (tagNum - hitNum);
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