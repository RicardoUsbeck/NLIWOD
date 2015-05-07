package org.aksw.hawk.nlp;

import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.aksw.autosparql.commons.qald.Question;
import org.aksw.autosparql.commons.qald.uri.Entity;

import com.clearnlp.dependency.DEPNode;
import com.clearnlp.dependency.DEPTree;
import com.clearnlp.nlp.NLPGetter;
import com.clearnlp.reader.AbstractReader;
import com.clearnlp.tokenization.AbstractTokenizer;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hp.hpl.jena.rdf.model.impl.ResourceImpl;

//TODO Talk to Micha about that whole static thing
public class SentenceToSequence {
	private static String language = AbstractReader.LANG_EN;
	static AbstractTokenizer tokenizer = NLPGetter.getTokenizer(language);

	// combine noun phrases
	public static void combineSequences(Question q) {
		// run pos-tagging
		String sentence = q.languageToQuestion.get("en");
		List<String> tokens = tokenizer.getTokens(sentence);
		Map<String, String> label2pos = generatePOSTags(q);

		// run phrase combination
		List<String> subsequence = Lists.newArrayList();
		for (int tcounter = 0; tcounter < tokens.size(); tcounter++) {
			String token = tokens.get(tcounter);
			String pos = label2pos.get(token);
			String nextPos = tcounter + 1 == tokens.size() ? null : label2pos.get(tokens.get(tcounter + 1));
			String lastPos = tcounter == 0 ? null : label2pos.get(tokens.get(tcounter - 1));

			// look for start "RB|JJ|NN(.)*"
			if (subsequence.isEmpty() && null != pos && pos.matches("CD|JJ|NN(.)*|RB(.)*")) {
				subsequence.add(token);
			}
			// split "of the" or "of all" or "against" via pos_i=IN and pos_i+1=DT
			else if (!subsequence.isEmpty() && null != pos && tcounter + 1 < tokens.size() && null != nextPos && pos.matches("IN") && nextPos.matches("(W)?DT|NNP(S)?")) {
				if (subsequence.size() > 1) {
					transformTree(subsequence, q);
				}
				subsequence = Lists.newArrayList();
			}
			// do not combine NNS and NNPS but combine "stage name",
			// "British Prime minister"
			else if (!subsequence.isEmpty() && null != pos && null != lastPos && lastPos.matches("NNS") && pos.matches("NNP(S)?")) {
				if (subsequence.size() > 2) {
					transformTree(subsequence, q);
				} 
				subsequence = Lists.newArrayList();
			}
			// finish via VB* or IN -> null or IN -> DT or WDT (now a that or
			// which follows)
			else if (!subsequence.isEmpty() && !lastPos.matches("JJ|HYPH") && (null == pos || pos.matches("VB(.)*|\\.|WDT") || (pos.matches("IN") && nextPos == null) || (pos.matches("IN") && nextPos.matches("DT")))) {
				// more than one token, so summarizing makes sense
				if (subsequence.size() > 1) {
					transformTree(subsequence, q);
				}
				subsequence = Lists.newArrayList();
			}
			// continue via "NN(.)*|RB|CD|CC|JJ|DT|IN|PRP|HYPH"
			else if (!subsequence.isEmpty() && null != pos && pos.matches("NN(.)*|RB|CD|CC|JJ|DT|IN|PRP|HYPH|VBN")) {
				subsequence.add(token);
			} else {
				subsequence = Lists.newArrayList();
			}
		}
	}

	private static Map<String, String> generatePOSTags(Question q) {
		ParseTree parse = new ParseTree();
		DEPTree tree = parse.process(q);
//TODO this is horribly wrong, the same label CAN have different pos tags
		Map<String, String> label2pos = Maps.newHashMap();
		Stack<DEPNode> stack = new Stack<DEPNode>();
		stack.push(tree.getFirstRoot());
		while (!stack.isEmpty()) {
			DEPNode tmp = stack.pop();
			label2pos.put(tmp.form, tmp.pos);
			for (DEPNode child : tmp.getDependentNodeList()) {
				stack.push(child);
			}
		}
		return label2pos;
	}

	private static void transformTree(List<String> subsequence, Question q) {
		String combinedNN = Joiner.on(" ").join(subsequence);
		String combinedURI = "http://aksw.org/combinedNN/" + Joiner.on("_").join(subsequence);

		Entity tmpEntity = new Entity();
		tmpEntity.label = combinedNN;
		tmpEntity.uris.add(new ResourceImpl(combinedURI));

		List<Entity> nounphrases = q.languageToNounPhrases.get("en");
		if (null == nounphrases) {
			nounphrases = Lists.newArrayList();
		}
		nounphrases.add(tmpEntity);
		q.languageToNounPhrases.put("en", nounphrases);
	}

	public static void main(String args[]) {
		Question q = new Question();
		q.languageToQuestion.put("en", "Who was vice-president under the president who authorized atomic weapons against Japan during World War II?");
//		q.languageToQuestion.put("en", "Who plays Phileas Fogg in the adaptation of Around the World in 80 Days directed by Buzz Kulik?");
		
		SentenceToSequence.combineSequences(q);
		System.out.println(q);
	}
}
