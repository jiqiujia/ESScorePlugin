package demo;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.ScriptPlugin;
import org.elasticsearch.script.*;
import org.elasticsearch.search.lookup.SearchLookup;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class ExpertScriptPlugin extends Plugin implements ScriptPlugin {

    @Override
    public ScriptEngineService getScriptEngineService(Settings settings) {
        return new MyExpertScriptEngine();
    }

    /**
     * An example {@link ScriptEngineService} that uses Lucene segment details to implement pure document frequency scoring.
     */
    // tag::expert_engine
    // compile方法返回我们定义好打分逻辑的java function。search方法用于我们在搜索过程中实施定义好的打分逻辑
    private static class MyExpertScriptEngine implements ScriptEngineService {
        // script type, used for `lang` field
        @Override
        public String getType() {
            return "expert_scripts";
        }

        @Override
        public Function<Map<String, Object>, SearchScript> compile(String scriptName, String scriptSource, Map<String, String> params) {
            // we use the script "source" as the script identifier
            if ("pure_df".equals(scriptSource)) {
                return p -> new SearchScript() {
                    final String field;
                    final String term;

                    {
                        if (!p.containsKey("field")) {
                            throw new IllegalArgumentException("Missing parameter [field]");
                        }
                        if (!p.containsKey("term")) {
                            throw new IllegalArgumentException("Missing parameter [term]");
                        }
                        field = p.get("field").toString();
                        term = p.get("term").toString();
                    }

                    @Override
                    public LeafSearchScript getLeafSearchScript(LeafReaderContext context) throws IOException {
                        PostingsEnum postings = context.reader().postings(new Term(field, term));
                        if (postings == null) {
                            // the field and/or term don't exist in this segment, so always return 0
                            return () -> 0.0d;
                        }
                        return new LeafSearchScript() {
                            int currentDocid = -1;

                            @Override
                            public void setDocument(int docid) {
                                // advance has undefined behavior calling with a docid <= its current docid
                                if (postings.docID() < docid) {
                                    try {
                                        postings.advance(docid);
                                    } catch (IOException e) {
                                        throw new UncheckedIOException(e);
                                    }
                                }
                                currentDocid = docid;
                            }

                            @Override
                            public double runAsDouble() {
                                if (postings.docID() != currentDocid) {
                                    // advance moved past the current doc, so this doc has no occurrences of the term
                                    return 0.0d;
                                }
                                try {
                                    return postings.freq();
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            }
                        };
                    }

                    @Override
                    public boolean needsScores() {
                        return false;
                    }
                };
            }
            throw new IllegalArgumentException("Unknown script name " + scriptSource);
        }

        @Override
        @SuppressWarnings("unchecked")
        public SearchScript search(CompiledScript compiledScript, SearchLookup lookup, @Nullable Map<String, Object> params) {
            Function<Map<String, Object>, SearchScript> scriptFactory = (Function<Map<String, Object>, SearchScript>) compiledScript.compiled();
            return scriptFactory.apply(params);
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
        public void close() {
        }
    }
    // end::expert_engine
}
