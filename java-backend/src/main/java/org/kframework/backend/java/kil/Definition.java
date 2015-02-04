// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.java.kil;

import org.kframework.backend.java.compile.KOREtoBackendKIL;
import org.kframework.backend.java.indexing.IndexingTable;
import org.kframework.backend.java.indexing.RuleIndex;
import org.kframework.backend.java.symbolic.ConjunctiveFormula;
import org.kframework.backend.java.symbolic.Transformer;
import org.kframework.backend.java.symbolic.Visitor;
import org.kframework.backend.java.util.Subsorts;
import org.kframework.compile.utils.ConfigurationStructureMap;
import org.kframework.definition.Module;
import org.kframework.kil.ASTNode;
import org.kframework.kil.Attribute;
import org.kframework.kil.Attributes;
import org.kframework.kil.DataStructureSort;
import org.kframework.kil.Production;
import org.kframework.kil.loader.Context;
import org.kframework.kore.KRewrite;
import org.kframework.krun.KRunOptions;
import org.kframework.main.GlobalOptions;
import org.kframework.utils.errorsystem.KExceptionManager;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import com.google.inject.Inject;
import scala.collection.JavaConversions;


/**
 * A K definition in the format of the Java Rewrite Engine.
 *
 * @author AndreiS
 */
public class Definition extends JavaSymbolicObject {

    private static class DefinitionData implements Serializable {
        public final Subsorts subsorts;
        public final Set<Sort> builtinSorts;
        public final Map<org.kframework.kil.Sort, DataStructureSort> dataStructureSorts;
        public final SetMultimap<String, SortSignature> signatures;
        public final ImmutableMap<String, Attributes> kLabelAttributes;
        public final SetMultimap<String, Production> listKLabels;
        public final Map<org.kframework.kil.Sort, String> freshFunctionNames;
        public final ConfigurationStructureMap configurationStructureMap;
        public final GlobalOptions globalOptions;
        public final KRunOptions kRunOptions;

        private DefinitionData(
                Subsorts subsorts,
                Set<Sort> builtinSorts,
                Map<org.kframework.kil.Sort, DataStructureSort> dataStructureSorts,
                SetMultimap<String, SortSignature> signatures,
                ImmutableMap<String, Attributes> kLabelAttributes,
                SetMultimap<String, Production> listKLabels,
                Map<org.kframework.kil.Sort, String> freshFunctionNames,
                ConfigurationStructureMap configurationStructureMap,
                GlobalOptions globalOptions,
                KRunOptions kRunOptions) {
            this.subsorts = subsorts;
            this.builtinSorts = builtinSorts;
            this.dataStructureSorts = dataStructureSorts;
            this.signatures = signatures;
            this.kLabelAttributes = kLabelAttributes;
            this.listKLabels = listKLabels;
            this.freshFunctionNames = freshFunctionNames;
            this.configurationStructureMap = configurationStructureMap;
            this.globalOptions = globalOptions;
            this.kRunOptions = kRunOptions;
        }
    }

    public static final Set<Sort> TOKEN_SORTS = ImmutableSet.of(
            Sort.BOOL,
            Sort.INT,
            Sort.FLOAT,
            Sort.CHAR,
            Sort.STRING,
            Sort.LIST,
            Sort.SET,
            Sort.MAP);

    private final List<Rule> rules = Lists.newArrayList();
    private final List<Rule> macros = Lists.newArrayList();
    private final Multimap<KLabelConstant, Rule> functionRules = ArrayListMultimap.create();
    private final Multimap<KLabelConstant, Rule> sortPredicateRules = HashMultimap.create();
    private final Multimap<KLabelConstant, Rule> anywhereRules = HashMultimap.create();
    private final Multimap<KLabelConstant, Rule> patternRules = ArrayListMultimap.create();
    private final List<Rule> patternFoldingRules = new ArrayList<>();

    private final Set<KLabelConstant> kLabels;

    private final DefinitionData definitionData;
    private final transient Context context;

    private transient KExceptionManager kem;

    private RuleIndex index;
    public final IndexingTable.Data indexingData;

    private final Map<KItem.CacheTableColKey, KItem.CacheTableValue> sortCacheTable = new HashMap<>();

    @Inject
    public Definition(Context context, KExceptionManager kem, IndexingTable.Data indexingData) {
        kLabels = new HashSet<>();
        this.kem = kem;
        this.indexingData = indexingData;

        ImmutableSet.Builder<Sort> builder = ImmutableSet.builder();
        // TODO(YilongL): this is confusing; give a better name to tokenSorts
        builder.addAll(Sort.of(context.getTokenSorts())); // e.g., [#String, #Int, Id, #Float]
        builder.addAll(TOKEN_SORTS); // [Bool, Int, Float, Char, String, List, Set, Map]

        ImmutableSetMultimap.Builder<String, SortSignature> signaturesBuilder = ImmutableSetMultimap.builder();
        for (Map.Entry<String, Production> entry : context.klabels.entries()) {
            ImmutableList.Builder<Sort> sortsBuilder = ImmutableList.builder();
            for (int i = 0; i < entry.getValue().getArity(); ++i) {
                sortsBuilder.add(Sort.of(entry.getValue().getChildSort(i)));
            }
            signaturesBuilder.put(
                    entry.getKey(),
                    new SortSignature(sortsBuilder.build(), Sort.of(entry.getValue().getSort())));
        }

        ImmutableMap.Builder<String, Attributes> attributesBuilder = ImmutableMap.builder();
        for (Map.Entry<String, Collection<Production>> entry : context.klabels.asMap().entrySet()) {
            Attributes attributes;
            if (!entry.getValue().isEmpty()) {
                attributes = entry.getValue().iterator().next().getAttributes();
                for (Production production : entry.getValue()) {
                    assert production.getAttributes().equals(attributes) :
                            "mismatch attributes:\n" + entry.getValue().iterator().next()
                            + "\nand\n" + production;
                }
            } else {
                attributes = new Attributes();
            }
            attributesBuilder.put(entry.getKey(), attributes);
        }

        definitionData = new DefinitionData(
                new Subsorts(context),
                builder.build(),
                context.getDataStructureSorts(),
                signaturesBuilder.build(),
                attributesBuilder.build(),
                context.listKLabels,
                context.freshFunctionNames,
                context.getConfigurationStructureMap(),
                context.globalOptions,
                context.krunOptions);
        this.context = context;
    }

    public Definition(org.kframework.definition.Module module, KExceptionManager kem) {
        kLabels = new HashSet<>();
        this.kem = kem;

        ImmutableSetMultimap.Builder<String, SortSignature> signaturesBuilder = ImmutableSetMultimap.builder();
        JavaConversions.mapAsJavaMap(module.signatureFor()).entrySet().stream().forEach(e -> {
            JavaConversions.setAsJavaSet(e.getValue()).stream().forEach(p -> {
                ImmutableList.Builder<Sort> sortsBuilder = ImmutableList.builder();
                JavaConversions.seqAsJavaList(p._1()).stream()
                        .map(s -> Sort.of(s.name()))
                        .forEach(sortsBuilder::add);
                signaturesBuilder.put(
                        e.getKey().name(),
                        new SortSignature(sortsBuilder.build(), Sort.of(p._2().name())));
            });
        });

        ImmutableMap.Builder<String, Attributes> attributesBuilder = ImmutableMap.builder();
        JavaConversions.mapAsJavaMap(module.attributesFor()).entrySet().stream().forEach(e -> {
            attributesBuilder.put(e.getKey().name(), new Attributes());
        });
        JavaConversions.setAsJavaSet(module.labelsToProductions().keySet()).stream().forEach(l -> {
            attributesBuilder.put(l.name(), new Attributes());
        });

        definitionData = new DefinitionData(
                new Subsorts(module),
                ImmutableSet.<Sort>builder()
                        .addAll(TOKEN_SORTS)
                        .add(Sort.of("#Int"))
                        .add(Sort.of("#String"))
                        .add(Sort.of("#Id"))
                        .build(),
                null,
                signaturesBuilder.build(),
                attributesBuilder.build(),
                ImmutableSetMultimap.<String, Production>builder().build(),
                null,
                null,
                new GlobalOptions(),
                new KRunOptions());
        context = null;

        this.indexingData = new IndexingTable.Data();
    }

    public void addKoreRules(Module module, TermContext termContext) {
        KOREtoBackendKIL transformer = new KOREtoBackendKIL(termContext);
        JavaConversions.setAsJavaSet(module.sentences()).stream().forEach(s -> {
            if (s instanceof org.kframework.definition.Rule) {
                org.kframework.definition.Rule rule = (org.kframework.definition.Rule) s;
                addRule(new Rule(
                        "",
                        transformer.convert(((KRewrite) rule.body()).left()),
                        transformer.convert(((KRewrite) rule.body()).right()),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptySet(),
                        Collections.emptySet(),
                        ConjunctiveFormula.of(termContext),
                        false,
                        null,
                        null,
                        null,
                        null,
                        new org.kframework.kil.Rule(),
                        termContext));
            }
        });
    }

    @Inject
    public Definition(DefinitionData definitionData, KExceptionManager kem, IndexingTable.Data indexingData) {
        kLabels = new HashSet<>();
        this.kem = kem;
        this.indexingData = indexingData;

        this.definitionData = definitionData;
        this.context = null;
    }

    public void addKLabel(KLabelConstant kLabel) {
        kLabels.add(kLabel);
    }

    public void addKLabelCollection(Collection<KLabelConstant> kLabels) {
        for (KLabelConstant kLabel : kLabels) {
            this.kLabels.add(kLabel);
        }
    }

    public void addRule(Rule rule) {
        if (rule.isFunction()) {
            functionRules.put(rule.definedKLabel(), rule);
            if (rule.isSortPredicate()) {
                sortPredicateRules.put((KLabelConstant) rule.sortPredicateArgument().kLabel(), rule);
            }
        } else if (rule.containsAttribute(Attribute.PATTERN_KEY)) {
            patternRules.put(rule.definedKLabel(), rule);
        } else if (rule.containsAttribute(Attribute.PATTERN_FOLDING_KEY)) {
            patternFoldingRules.add(rule);
        } else if (rule.containsAttribute(Attribute.MACRO_KEY)) {
            macros.add(rule);
        } else if (rule.containsAttribute(Attribute.ANYWHERE_KEY)) {
            if (!(rule.leftHandSide() instanceof KItem)) {
                kem.registerCriticalWarning(
                        "The Java backend only supports [anywhere] rule that rewrites KItem; but found:\n\t"
                                + rule, rule);
                return;
            }

            anywhereRules.put(rule.anywhereKLabel(), rule);
        } else {
            rules.add(rule);
        }
    }

    public void addRuleCollection(Collection<Rule> rules) {
        for (Rule rule : rules) {
            addRule(rule);
        }
    }

    /**
     * TODO(YilongL): this name is really confusing; looks like it's only used
     * in building index;
     */
    public Set<Sort> builtinSorts() {
        return definitionData.builtinSorts;
    }

    public Set<Sort> allSorts() {
        return definitionData.subsorts.allSorts();
    }

    public Subsorts subsorts() {
        return definitionData.subsorts;
    }

    public void setContext(Context context) {
    }

    public void setKem(KExceptionManager kem) {
        this.kem = kem;
    }

    public Multimap<KLabelConstant, Rule> functionRules() {
        return functionRules;
    }

    public Multimap<KLabelConstant, Rule> anywhereRules() {
        return anywhereRules;
    }

    public Collection<Rule> sortPredicateRulesOn(KLabelConstant kLabel) {
        if (sortPredicateRules.isEmpty()) {
            return Collections.emptyList();
        }
        return sortPredicateRules.get(kLabel);
    }

    public Multimap<KLabelConstant, Rule> patternRules() {
        return patternRules;
    }

    public List<Rule> patternFoldingRules() {
        return patternFoldingRules;
    }

    public Set<KLabelConstant> kLabels() {
        return Collections.unmodifiableSet(kLabels);
    }

    public List<Rule> macros() {
        // TODO(AndreiS): fix this issue with modifiable collections
        //return Collections.unmodifiableList(macros);
        return macros;
    }

    public List<Rule> rules() {
        // TODO(AndreiS): fix this issue with modifiable collections
        //return Collections.unmodifiableList(rules);
        return rules;
    }

    @Override
    public ASTNode accept(Transformer transformer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void accept(Visitor visitor) {
        throw new UnsupportedOperationException();
    }

    public void setIndex(RuleIndex index) {
        this.index = index;
    }

    public RuleIndex getIndex() {
        return index;
    }

    public Map<KItem.CacheTableColKey, KItem.CacheTableValue> getSortCacheTable() {
        return sortCacheTable;
    }

    // added from context
    public Set<SortSignature> signaturesOf(String label) {
        return definitionData.signatures.get(label);
    }

    public Map<String, Attributes> kLabelAttributes() {
        return definitionData.kLabelAttributes;
    }

    public Attributes kLabelAttributesOf(String label) {
        return definitionData.kLabelAttributes.get(label);
    }

    public Set<Production> listLabelsOf(String label) {
        return definitionData.listKLabels.get(label);
    }

    public ConfigurationStructureMap getConfigurationStructureMap() {
        return definitionData.configurationStructureMap;
    }

    public DataStructureSort dataStructureSortOf(Sort sort) {
        return definitionData.dataStructureSorts.get(sort.toFrontEnd());
    }

    public GlobalOptions globalOptions() {
        return definitionData.globalOptions;
    }

    public KRunOptions kRunOptions() {
        return definitionData.kRunOptions;
    }

    public Map<org.kframework.kil.Sort, String> freshFunctionNames() {
        return definitionData.freshFunctionNames;
    }

    public DefinitionData definitionData() {
        return definitionData;
    }

    public Context context() {
        return context;
    }

}
