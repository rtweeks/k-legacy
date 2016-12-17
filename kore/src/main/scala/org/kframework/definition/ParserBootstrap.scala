package org.kframework.definition

import org.kframework.attributes.Att
import org.kframework.builtin.Sorts
import org.kframework.kore.ADT._
import org.kframework.kore._
import org.kframework.meta.Down

import collection.JavaConverters._

/**
  * Created by lpena on 10/11/16.
  */

object KDefinitionDSL {

  def asKApply(label: String, values: List[String]): K =
    KORE.KApply(KORE.KLabel(label), KORE.KList(values map { value => KORE.KToken(value, Sorts.KString, Att()) }), Att())
  def asKApply(label: String, values: String*): K = asKApply(label, values toList)

  implicit def asAttribute(str: String): K = asKApply(str, List.empty)
  implicit def asNonTerminal(s: ADT.SortLookup): NonTerminal = NonTerminal(s)
  implicit def asTerminal(s: String): Terminal = Terminal(s)
  implicit def asProduction(ps: ProductionItem*): Seq[ProductionItem] = ps
  implicit def asSentence(bp: BecomingSyntax): Sentence = Production(bp.sort, bp.pis, Att())
  implicit def asSentence(bp: BecomingSyntaxSort): Sentence = SyntaxSort(bp.sort, Att())

  def Sort(s: String): ADT.SortLookup = ADT.SortLookup(s)

  def regex(s: String): ProductionItem = RegexTerminal("#", s, "#")

  case class syntax(s: ADT.SortLookup) {
    def is(pis: ProductionItem*): BecomingSyntax = BecomingSyntax(s, pis)
  }
  case class BecomingSyntax(sort: ADT.SortLookup, pis: Seq[ProductionItem]) {
    def att(atts: K*): Sentence = Production(sort, pis, atts.foldLeft(Att())(_+_))
  }

  def sort(sort: ADT.SortLookup): BecomingSyntaxSort = BecomingSyntaxSort(sort)
  case class BecomingSyntaxSort(sort: ADT.SortLookup) {
    def att(atts: K*): Sentence = SyntaxSort(sort, atts.foldLeft(Att())(_+_))
  }

  def imports(s: Module*): Set[Module] = s.toSet
  def sentences(s: Sentence*): Set[Sentence] = s.toSet
  def khook(label: String): K = asKApply("hook", List(label))
  def klabel(label: String): K = asKApply("klabel", List(label))
  def ktoken(label: String): K = asKApply("ktoken", List(label))
}

object KoreDefintion {
  import KDefinitionDSL._

  var KORE_STRING =
    """
    module KSORT
      syntax K [hook(K.K)]
    endmodule
    """

  val K = Sort("K")
  val KSORT = Module("KSORT", imports(), sentences(
    sort(K) att khook("K.K")
  ))

  KORE_STRING +=
    """
    module KBASIC
      imports KSORT

      syntax KLabel
      syntax KItem        [hook(K.KItem)]
      syntax KConfigVar
      syntax KBott
      syntax KResult
      syntax MetaVariable
      syntax Bottom

      syntax K ::= KItem  [allowChainSubsort]

      syntax KList ::= K                 [allowChainSubsort]
      syntax KList ::= ".KList"          [klabel(#EmptyKList), hook(org.kframework.kore.EmptyKList)]
      syntax KList ::= ".::KList"        [klabel(#EmptyKList), hook(org.kframework.kore.EmptyKList)]
      syntax KList ::= KList "," KList   [klabel(#KList), left, assoc, unit(#EmptyKList), hook(org.kframework.kore.KList), prefer]
    endmodule
    """

  val KLabel = Sort("KLabel")
  val KItem = Sort("KItem")
  val KConfigVar = Sort("KConfigVar")
  val KBott = Sort("KBott")
  val KList = Sort("KList")
  val KResult = Sort("KResult")
  val MetaVariable = Sort("MetaVariable")
  val Bottom = Sort("Bottom")
  val KBASIC = Module("KBASIC", imports(KSORT), sentences(
    sort(KLabel),
    sort(KItem) att khook("K.KItem"),
    sort(KConfigVar),
    sort(KBott),
    sort(KResult),
    sort(MetaVariable),
    sort(Bottom),

    syntax(K) is KItem att "allowChainSubsort",

    syntax(KList) is K att "allowChainSubsort",
    syntax(KList) is (".KList") att(klabel("#EmptyKList"), khook("org.kframework.kore.EmptyKList")),
    syntax(KList) is (".::KList") att(klabel("#EmptyKList"), khook("org.kframework.kore.EmptyKList")),
    syntax(KList) is (KList, ",", KList) att(klabel("#KList"), "left", "assoc", asKApply("unit", "#EmptyKList"), khook("org.kframework.kore.KList"), "prefer")
  ))

  val KStringRegex = """[\\\"](([^\\\"\n\r\\\\])|([\\\\][nrtf\\\"\\\\])|([\\\\][x][0-9a-fA-F]{2})|([\\\\][u][0-9a-fA-F]{4})|([\\\\][U][0-9a-fA-F]{8}))*[\\\"]"""
  KORE_STRING +=
    """
    module KSTRING
      syntax KString ::= r""" + "\"" + KStringRegex + "\"" + """ [token, hook(org.kframework.kore.KString)]
    endmodule
    """

  val KString = Sort("KString")
  val KSTRING = Module("KSTRING", imports(), sentences(
    syntax(KString) is regex(KStringRegex) att("token", khook("org.kframework.kore.KString"))
  ))


  val KRegexAttributeKey1 = """`(\\\\`|\\\\\\\\|[^`\\\\\n\r])+`"""
  val KRegexAttributeKey2 = """(?<![a-zA-Z0-9])[#a-z][a-zA-Z0-9@\\-]*"""
  KORE_STRING +=
    """
    module KATTRIBUTES
      imports KSTRING

      syntax KAttributeKey ::= """ + "r\"" + KRegexAttributeKey1 + "r\"" + """ [token, hook(org.kframework.kore.KLabel)]
      syntax KAttributeKey ::= """ + "r\"" + KRegexAttributeKey2 + "r\"" + """ [token, hook(org.kframework.kore.KLabel), autoReject]

      syntax KKeyList ::= KAttributeKey
      syntax KKeyList ::= "" [klabel(.KKeyList)]
      syntax KKeyList ::= KKeyList "," KKeyList [klabel(KKeyList)]

      syntax KAttribute ::= KAttributeKey
      syntax KAttribute ::= KAttributeKey "(" KKeyList ")" [klabel(KAttributeApply)]

      syntax KAttributes ::= KAttribute
      syntax KAttributes ::= "" [klabel(.KAttributes)]
      syntax KAttributes ::= KAttributes "," KAttributes [klabel(KAttributes)]

      syntax KBott ::= KAttributes
      syntax KItem ::= KBott                                 [allowChainSubsort]
    endmodule
    """

  val KAttributeKey = Sort("KAttributeKey")
  val KKeyList = Sort("KKeyList")
  val KAttribute= Sort("KAttribute")
  val KAttributes= Sort("KAttributes")
  val KATTRIBUTES = Module("KATTRIBUTES", imports(KSTRING), sentences(
    syntax(KAttributeKey) is regex(KRegexAttributeKey1) att("token", khook("org.kframework.kore.KLabel")),
    syntax(KAttributeKey) is regex(KRegexAttributeKey2) att("token", khook("org.kframework.kore.KLabel"), "autoReject"),

    syntax(KKeyList) is KAttributeKey,
    syntax(KKeyList) is "" att klabel(".KKeyList"),
    syntax(KKeyList) is (KKeyList, ",", KKeyList) att klabel("KKeyList"),

    syntax(KAttribute) is KAttributeKey,
    syntax(KAttribute) is (KAttributeKey, "(", KKeyList, ")") att klabel("KAttributeApply"),

    syntax(KAttributes) is KAttribute,
    syntax(KAttributes) is "" att klabel(".KAttributes"),
    syntax(KAttributes) is (KAttributes, ",", KAttributes) att klabel("KAttributes")
  ))

  var KORE = Map("KSORT" -> KSORT, "KBASIC" -> KBASIC, "KSTRING" -> KSTRING, "KATTRIBUTES" -> KATTRIBUTES)

  val REST_STRING =
    """
    // To be used when parsing/pretty-printing ground configurations
    module KSEQ
      imports KAST
      imports K-TOP-SORT
      syntax KBott ::= ".K"      [klabel(#EmptyK), hook(org.kframework.kore.EmptyK)]
                     | "."       [klabel(#EmptyK), hook(org.kframework.kore.EmptyK)]
                     | ".::K"    [klabel(#EmptyK), hook(org.kframework.kore.EmptyK)]
                     | K "~>" K  [klabel(#KSequence), left, assoc, unit(#EmptyK), hook(org.kframework.kore.KSequence)]
      syntax left #KSequence
      syntax KBott     ::= "(" K ")"    [bracket]
    endmodule

    // To be used when parsing/pretty-printing symbolic configurations
    module KSEQ-SYMBOLIC
      imports KSEQ
      syntax #KVariable ::= r"(?<![A-Za-z0-9_\\$!\\?])(\\!|\\?)?([A-Z][A-Za-z0-9'_]*|_)"   [token, autoReject, hook(org.kframework.kore.KVariable)]
      syntax KConfigVar ::= r"(?<![A-Za-z0-9_\\$!\\?])(\\$)([A-Z][A-Za-z0-9'_]*)"          [token, autoReject]
      syntax KBott      ::= #KVariable [allowChainSubsort]
      syntax KBott      ::= KConfigVar [allowChainSubsort]
      syntax KLabel     ::= #KVariable [allowChainSubsort]
    endmodule

    module KCELLS
      imports KAST

      syntax Cell
      syntax Bag ::= Bag Bag  [left, assoc, klabel(#cells), unit(#cells)]
                   | ".Bag"   [klabel(#cells)]
                   | ".::Bag" [klabel(#cells)]
                   | Cell     [allowChainSubsort]
      syntax Bag ::= "(" Bag ")" [bracket]
      syntax K ::= Bag
      syntax Bag ::= KBott
    endmodule

    module RULE-CELLS
      imports KCELLS
      // if this module is imported, the parser automatically
      // generates, for all productions that have the attribute 'cell' or 'maincell',
      // a production like below:
      //syntax Cell ::= "<top>" #OptionalDots K #OptionalDots "</top>" [klabel(<top>)]

      syntax #OptionalDots ::= "..." [klabel(#dots)]
                             | ""    [klabel(#noDots)]
    endmodule

    module RULE-PARSER
      imports RULE-LISTS
      imports RULE-CELLS
      // imported in modules which generate rule parsers
      // TODO: (radumereuta) don't use modules as markers to generate parsers
    endmodule

    module CONFIG-CELLS
      imports KCELLS
      imports RULE-LISTS
      syntax #CellName ::= r"[a-zA-Z0-9\\-]+"  [token]

      syntax Cell ::= "<" #CellName #CellProperties ">" K "</" #CellName ">" [klabel(#configCell)]
      syntax Cell ::= "<" #CellName "/>" [klabel(#externalCell)]
      syntax Cell ::= "<br" "/>" [klabel(#breakCell)]

      syntax #CellProperties ::= #CellProperty #CellProperties [klabel(#cellPropertyList)]
                               | ""                            [klabel(#cellPropertyListTerminator)]
      syntax #CellProperty ::= #CellName "=" KString           [klabel(#cellProperty)]

    endmodule

    module REQUIRES-ENSURES
      imports BASIC-K

      syntax RuleContent ::= K                                 [klabel("#ruleNoConditions"), allowChainSubsort, latex({#1}{}{})]
                           | K "requires" K                    [klabel("#ruleRequires"), latex({#1}{#2}{})]
                           | K "when" K                        [klabel("#ruleRequires"), latex({#1}{#2}{})]
                           | K "ensures"  K                    [klabel("#ruleEnsures"), latex({#1}{}{#3})]
                           | K "requires" K "ensures" K        [klabel("#ruleRequiresEnsures"), latex({#1}{#2}{#3})]
                           | K "when" K "ensures" K            [klabel("#ruleRequiresEnsures"), latex({#1}{#2}{#3})]
    endmodule

    module K-TOP-SORT
      // if this module is imported, the parser automatically
      // generates, for all sorts, productions of the form:
      // K     ::= Sort
      // this is part of the mechanism that allows concrete user syntax in K
    endmodule

    module K-BOTTOM-SORT
      // if this module is imported, the parser automatically
      // generates, for all sorts, productions of the form:
      // Sort  ::= KBott
      // this is part of the mechanism that allows concrete user syntax in K
    endmodule

    module K-SORT-LATTICE
      imports K-TOP-SORT
      imports K-BOTTOM-SORT
    endmodule

    module AUTO-CASTS
      // if this module is imported, the parser automatically
      // generates, for all sorts, productions of the form:
      // Sort  ::= Sort "::Sort"
      // Sort  ::= Sort ":Sort"
      // KBott ::= Sort "<:Sort"
      // Sort  ::= K    ":>Sort"
      // this is part of the mechanism that allows concrete user syntax in K
    endmodule

    module AUTO-FOLLOW
      // if this module is imported, the parser automatically
      // generates a follow restriction for every terminal which is a prefix
      // of another terminal. This is useful to prevent ambiguities such as:
      // syntax K ::= "a"
      // syntax K ::= "b"
      // syntax K ::= "ab"
      // syntax K ::= K K
      // #parse("ab", "K")
      // In the above example, the terminal "a" is not allowed to be followed by a "b"
      // because it would turn the terminal into the terminal "ab".
    endmodule

    module PROGRAM-LISTS
      imports SORT-K
      // if this module is imported, the parser automatically
      // replaces the default productions for lists:
      // Es ::= E "," Es [userList("*"), klabel('_,_)]
      //      | ".Es"    [userList("*"), klabel('.Es)]
      // into a series of productions more suitable for programs:
      // Es#Terminator ::= ""      [klabel('.Es)]
      // Ne#Es ::= E "," Ne#Es     [klabel('_,_)]
      //         | E Es#Terminator [klabel('_,_)]
      // Es ::= Ne#Es
      //      | Es#Terminator      // if the list is *
    endmodule

    module RULE-LISTS
      // if this module is imported, the parser automatically
      // adds the subsort production to the parsing module only:
      // Es ::= E        [userList("*")]

    endmodule

    module DEFAULT-CONFIGURATION
      imports BASIC-K

      configuration <k> $PGM:K </k>
    endmodule

    // To be used to parse semantic rules
    module K
      imports KSEQ-SYMBOLIC
      imports REQUIRES-ENSURES
      imports K-SORT-LATTICE
      imports AUTO-CASTS
      imports AUTO-FOLLOW
      syntax KBott     ::= K "=>" K     [klabel(#KRewrite), hook(org.kframework.kore.KRewrite), non-assoc]
      syntax non-assoc #KRewrite

    endmodule

    // To be used to parse terms in full K
    module K-TERM
      imports KSEQ-SYMBOLIC
      imports K-SORT-LATTICE
      imports AUTO-CASTS
      imports AUTO-FOLLOW
    endmodule
    """

//  val KRegexSort = "[A-Z][A-Za-z0-9]*"
//  val KRegexAttributeKey = "[\\.A-Za-z\\-0-9]*"
//  val KRegexModuleName = "[A-Z][A-Z\\-]*"
//  def rString(str: String) : String = "r\"" + str + "\""
//
//  val KTOKENS_STRING =
//    """
//    module KTOKENS
//      imports KSTRING
//
//      syntax KString ::= """ + rString(KStringRegex) + """ [token, klabel(KString)]
//      syntax KSort ::= """ + rString(KRegexSort) + """ [token, klabel(KSort)]
//      syntax KAttributeKey ::= """ + rString(KRegexAttributeKey) + """ [token, klabel(KAttributeKey)]
//      syntax KModuleName ::= """ + rString(KRegexModuleName) + """ [token, klabel(KModuleName)]
//    endmodule
//    """

//  val KSort = Sort("KSort")
//  val KModuleName = Sort("KModuleName")
//
//  val KTOKENS = Module("KTOKENS", imports(), sentences(
//
//    syntax(KString) is regex(KStringRegex) att ("token", klabel("KString")),
//    syntax(KSort) is regex(KRegexSort) att ("token", klabel("KSort")),
//    syntax(KAttributeKey) is regex(KRegexAttributeKey) att ("token", klabel("KAttributeKey")),
//    syntax(KModuleName) is regex(KRegexModuleName) att ("token", klabel("KModuleName"))
//
//  ))

  val KML_STRING =
    """
    module KML
      imports KSTRING

      syntax KMLVar ::= "kmlvar" "(" KString ")" [klabel(kmlvar)]
      syntax KMLFormula ::= KMLVar
      syntax KMLFormula ::= "KMLtrue" [klabel(KMLtrue)]
      syntax KMLFormula ::= "KMLfalse" [klabel(KMLfalse)]
      syntax KMLFormula ::= KMLFormula "KMLand" KMLFormula [klabel(KMLand)]
      syntax KMLFormula ::= KMLFormula "KMLor" KMLFormula [klabel(KMLor)]
      syntax KMLFormula ::= "KMLnot" KMLFormula [klabel(KMLnot)]
      syntax KMLFormula ::= "KMLexists" KMLVar "." KMLFormula [klabel(KMLexists)]
      syntax KMLFormula ::= "KMLforall" KMLVar "." KMLFormula [klabel(KMLforall)]
      syntax KMLFormula ::= KMLFormula "KML=>" KMLFormula [klabel(KMLnext)]
    endmodule
    """

  val KMLVar = Sort("KMLVar")
  val KMLFormula = Sort("KMLFormula")

  val KML = Module("KML", imports(KSTRING), sentences(

    syntax(KMLVar) is ("kmlvar", "(", KString, ")") att klabel("kmlvar"),
    syntax(KMLFormula) is KMLVar,
    syntax(KMLFormula) is "KMLtrue" att klabel("KMLtrue"),
    syntax(KMLFormula) is "KMLfalse" att klabel("KMLfalse"),
    syntax(KMLFormula) is (KMLFormula, "KMLand", KMLFormula) att klabel("KMLand"),
    syntax(KMLFormula) is (KMLFormula, "KMLor", KMLFormula) att klabel("KMLor"),
    syntax(KMLFormula) is ("KMLnot", KMLFormula) att klabel("KMLnot"),
    syntax(KMLFormula) is ("KMLexists", KMLVar, ".", KMLFormula) att klabel("KMLexists"),
    syntax(KMLFormula) is ("KMLforall", KMLVar, ".", KMLFormula) att klabel("KMLforall"),
    syntax(KMLFormula) is (KMLFormula, "KML=>", KMLFormula) att klabel("KMLnext")

  ))

  val KSENTENCES_STRING =
    """
    module KSENTENCES
      imports KATTRIBUTES
      imports KML

      syntax KImport ::= "imports" KModuleName [klabel(KImport)]
      syntax KImportList ::= "" [klabel(.KImportList)]
      syntax KImportList ::= KImport KImportList [klabel(KImportList)]
      syntax KTerminal ::= KString [.KAttributes]
      syntax KTerminal ::= "r" KString [klabel(KRegex)]
      syntax KNonTerminal ::= KSort [.KAttributes]
      syntax KProductionItem ::= KTerminal [.KAttributes]
      syntax KProductionItem ::= KNonTerminal [.KAttributes]
      syntax KProduction ::= KProductionItem [.KAttributes]
      syntax KProduction ::= KProductionItem KProduction [klabel(KProduction)]
      syntax KPreSentence ::= "syntax" KSort [klabel(KSortDecl)]
      syntax KPreSentence ::= "syntax" KSort "::=" KProduction [klabel(KSyntax)]
      syntax KSentence ::= KPreSentence [.KAttributes]
      syntax KSentence ::= KPreSentence "[" KAttributes "]" [klabel(KSentence)]
      syntax KSentenceList ::= "" [klabel(.KSentenceList)]
      syntax KSentenceList ::= KSentence KSentenceList [klabel(KSentenceList)]
    endmodule
    """

  val KModuleName = Sort("KModuleName")
  val KImport = Sort("KImport")
  val KImportList = Sort("KImportList")

  val KSort = Sort("KSort")
  val KTerminal = Sort("KTerminal")
  val KNonTerminal = Sort("KNonTerminal")
  val KProductionItem = Sort("KProductionItem")
  val KProduction = Sort("KProduction")

  val KSentence = Sort("KSentence")
  val KPreSentence = Sort("KPreSentence")
  val KSentenceList = Sort("KSentenceList")

  val KSENTENCES = Module("KSENTENCES", imports(KATTRIBUTES, KML), sentences(

    syntax(KImport) is ("imports", KModuleName) att klabel("KImport"),
    syntax(KImportList) is "" att klabel(".KImportList"),
    syntax(KImportList) is (KImport, KImportList) att klabel("KImportList"),

    syntax(KTerminal) is KString,
    syntax(KTerminal) is ("r", KString) att klabel("KRegex"),
    syntax(KNonTerminal) is KSort,
    syntax(KProductionItem) is KTerminal,
    syntax(KProductionItem) is KNonTerminal,
    syntax(KProduction) is KProductionItem,
    syntax(KProduction) is (KProductionItem, KProduction) att klabel("KProduction"),

    syntax(KPreSentence) is ("syntax", KSort) att klabel("KSortDecl"),
    syntax(KPreSentence) is ("syntax", KSort, "::=", KProduction) att klabel("KSyntax"),

    syntax(KSentence) is KPreSentence,
    syntax(KSentence) is (KPreSentence, "[", KAttributes, "]") att klabel("KSentence"),

    syntax(KSentenceList) is "" att klabel(".KSentenceList"),
    syntax(KSentenceList) is (KSentence, KSentenceList) att klabel("KSentenceList")
  ))

  val KRegexModuleName = "[A-Z][A-Z\\-]*"
  val KDEFINITION_STRING =
    """
    module KDEFINITION
      imports KSENTENCES

      syntax(KModuleName) is """ + "r\"" + KRegexModuleName + "\"" + """ [token, klabel(KModuleName)]

      syntax KRequire ::= "require" KString [klabel(KRequire)]
      syntax KRequireList ::= "" [klabel(.KRequireList)]
      syntax KRequireList ::= KRequire KRequireList [klabel(KRequireList)]

      syntax KModule ::= "module" KModuleName KImportList KSentenceList "endmodule" [klabel(KModule)]
      syntax KModuleList ::= "" [klabel(.KModuleList)]
      syntax KModuleList ::= KModule KModuleList [klabel(KModuleList)]

      syntax KDefinition ::= KRequireList KModuleList [klabel(KDefinition)]
    endmodule
    """

  val KModule = Sort("KModule")
  val KModuleList = Sort("KModuleList")

  val KRequire = Sort("KRequire")
  val KRequireList = Sort("KRequireList")
  val KDefinition = Sort("KDefinition")

  val KDEFINITION = Module("KDEFINITION", imports(KSENTENCES), sentences(

    syntax(KModuleName) is regex(KRegexModuleName) att("token", klabel("KModuleName")),

    syntax(KRequire) is ("require", KString) att klabel("KRequire"),
    syntax(KRequireList) is "" att klabel(".KRequireList"),
    syntax(KRequireList) is (KRequire, KRequireList) att klabel("KRequireList"),

    syntax(KModule) is ("module", KModuleName, KImportList, KSentenceList, "endmodule") att klabel("KModule"),
    syntax(KModuleList) is "" att klabel(".KModuleList"),
    syntax(KModuleList) is (KModule, KModuleList) att klabel("KModuleList"),

    syntax(KDefinition) is (KRequireList, KModuleList) att klabel("KDefinition")
  ))

  val KOREDEF_STRING = KORE_STRING + "\n" + KML_STRING + "\n" + KSENTENCES_STRING + "\n" + KDEFINITION_STRING
  val KOREDEF = Map( "KSORT" -> KSORT
                   , "KBASIC" -> KBASIC
                   , "KSTRING" -> KSTRING
                   , "KATTRIBUTES" -> KATTRIBUTES
                   , "KML" -> KML
                   , "KSENTENCES" -> KSENTENCES
                   , "KDEFINITION" -> KDEFINITION
                   )
}

object KoreDefinitionDown {
  import KDefinitionDSL._
  import KoreDefintion._
  import ADT.KList

  def downKKeyList(parsedKKeyList: K): List[String] = parsedKKeyList match {
    case KApply(KLabelLookup("KKeyList"), KList(list1 :: list2 :: _), _) => downKKeyList(list1) ++ downKKeyList(list2)
    case KApply(KLabelLookup("KAttributeKey"), KList(KToken(att, KAttributeKey, _) :: _), _) => List(att)
    case _ => List.empty
  }

  def downAttributes(parsedAttributes: K): Att = parsedAttributes match {
    case KApply(KLabelLookup("KAttributes"), KList(atts1 :: atts2 :: _), _) => downAttributes(atts1) ++ downAttributes(atts2)
    case KApply(KLabelLookup("KAttributeApply"), KList(KToken(fnc, KAttributeKey, _) :: keyList :: _), _) => Att(asKApply(fnc, downKKeyList(keyList)))
    case KToken(attName, KAttributeKey, _) => Att(attName)
    case _ => Att()
  }

  def downProduction(parsedProduction: K): Seq[ProductionItem] = parsedProduction match {
    case KApply(KLabelLookup("KProduction"), KList(prodItem :: rest :: _), _) => downProduction(prodItem) ++ downProduction(rest)
    case KApply(KLabelLookup("KRegex"), KList(KToken(str, KString, _) :: _), _) => Seq(RegexTerminal("#", str.drop(1).dropRight(1), "#"))
    case KToken(str, KString, _) => Seq(Terminal(str.drop(1).dropRight(1)))
    case KToken(sortName, KSort, _) => Seq(NonTerminal(Sort(sortName)))
    case _ => Seq.empty
  }

  def downSentences(parsedSentence: K, atts: Att = Att()): Set[Sentence] = parsedSentence match {
    case KApply(KLabelLookup("KSentenceList"), KList(sentence :: rest :: _), _) => downSentences(sentence, atts) ++ downSentences(rest, atts)
    case KApply(KLabelLookup("KSentence"), KList(preSentence :: newAtts :: _), _) => downSentences(preSentence, downAttributes(newAtts))
    case KApply(KLabelLookup("KSortDecl"), KList(KToken(sortName, _, _) :: _), _) => Set(SyntaxSort(Sort(sortName), atts))
    case KApply(KLabelLookup("KSyntax"), KList(KToken(sortName, _, _) :: prod :: _), _) => Set(Production(Sort(sortName), downProduction(prod), atts))
    case _ => Set.empty
  }

  def downImports(parsedImports: K): List[String] = parsedImports match {
    case KApply(KLabelLookup("KImportList"), KList(importStmt :: rest :: _), _) => downImports(importStmt) ++ downImports(rest)
    case KApply(KLabelLookup("KImport"), KList(KToken(importModule, KModuleName, _) :: _), _) => List(importModule)
    case _ => List.empty
  }

  // TODO: Make this chase the requires list
  def downModules(parsedModule: K, downedModules: Map[String, Module]): Map[String, Module] = parsedModule match {
    case KApply(KLabelLookup("KDefinition"), KList(requires :: modules :: _), _) => downModules(modules, downModules(requires, downedModules))
    case KApply(KLabelLookup("KRequireList"), _, _) => downedModules
    case KApply(KLabelLookup("KModuleList"), KList(module :: modules :: _), _) => downModules(modules, downModules(module, downedModules))
    case KApply(KLabelLookup("KModule"), KList(KToken(name, _, _) :: imports :: sentences :: _), _)
      => downedModules ++ Map(name -> Module(name, downImports(imports) map downedModules toSet, downSentences(sentences)))
    case _ => downedModules
  }
}