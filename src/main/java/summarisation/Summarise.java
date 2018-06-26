package summarisation;

import summarisation.parser.Sentence;
import summarisation.parser.StanfordParser;
import summarisation.parser.Token;
import summarisation.parser.Undesirables;

import java.io.IOException;
import java.util.*;

public class Summarise {

    private Undesirables undesirables;  // stop words
    private StanfordParser parser; // parsing things


    public Summarise() {
        undesirables = new Undesirables();
        parser = new StanfordParser();
        parser.init();
    }

    /**
     * pre-process all the text - return a summary of the word frequencies and the parsed text itself
     *
     * @param text the text to process
     * @return the pre processing results for this text
     */
    private SummarisePreProcessResult preProcessText(String text) throws IOException {
        List<Sentence> sentenceList = parser.parse(text);
        Map<String, Integer> frequencyMap = new HashMap<>();
        List<Sentence> finalSentenceList = new ArrayList<>();
        int longestSentence = 0;
        for (Sentence sentence : sentenceList) {
            List<Token> newTokenList = new ArrayList<>();
            for (Token token :sentence.getTokenList()) {
                if (!undesirables.contains(token.getLemma())) {
                    newTokenList.add(token);
                }
            }
            if (newTokenList.size() > 0) {
                for (Token t : newTokenList) {
                    if (!frequencyMap.containsKey(t.getLemma())) {
                        frequencyMap.put(t.getLemma(), 1);
                    } else {
                        frequencyMap.put(t.getLemma(), frequencyMap.get(t.getLemma()) + 1);
                    }
                }
                finalSentenceList.add(new Sentence(newTokenList));
                if (newTokenList.size() > longestSentence) {
                    longestSentence = newTokenList.size();
                }
            }
        }
        return new SummarisePreProcessResult(finalSentenceList, frequencyMap, longestSentence);
    }

    /**
     * return a score for each sentence vis a vie title scoring
     * @param sentenceList the list of sentences to check
     * @param title the title's tokens
     * @return a list of floats, one for each sentence on how it scores
     */
    private List<Float> getTitleFeatures(List<Sentence> sentenceList, List<Token> title) {
        // setup a faster lookup
        Set<String> titleLookup = new HashSet<>();
        for (Token token : title) {
            titleLookup.add(token.getLemma());
        }
        List<Float> sentenceTitleFeatures = new ArrayList<>();
        for (Sentence sentence : sentenceList) {
            float count = 0.0f;
            if (title.size() > 0) {
                for (Token token : sentence.getTokenList()) {
                    if (titleLookup.contains(token.getLemma())) {
                        count += 1.0f;
                    }
                }
            }
            sentenceTitleFeatures.add(count / (float)title.size());
        }
        return sentenceTitleFeatures;
    }

    /**
     * return a list of how the sentences correspond to the longest sentence from [0.0, 1.0]
     * @param sentenceList the list of sentences to check
     * @param longestSentence the size of the longest sentence
     * @return a list of scores for each sentence one
     */
    private List<Float> getSentenceLengthFeatures(List<Sentence> sentenceList, int longestSentence) {
        List<Float> sentenceLengthFeatures = new ArrayList<>();
        float longestS = (float)longestSentence;
        for (Sentence sentence : sentenceList) {
            if (longestS > 0.0f) {
                sentenceLengthFeatures.add((float)sentence.getTokenList().size() / longestS);
            } else {
                sentenceLengthFeatures.add(0.0f);
            }
        }
        return sentenceLengthFeatures;
    }

    /**
     * return a count of the number of sentences to token appears in
     * @param sentenceList the set of sentences
     * @param token the token to check
     * @return the count of the number of sentences token appears in
     */
    private int getWordSentenceCount(List<Sentence> sentenceList, Token token, Map<String, Integer> cache) {
        if (cache.containsKey(token.getLemma())) {
            return cache.get(token.getLemma());
        } else {
            int numSentences = 0;
            for (Sentence sentence : sentenceList) {
                for (Token t : sentence.getTokenList()) {
                    if (t.getLemma().compareToIgnoreCase(token.getLemma()) == 0) {
                        numSentences += 1;
                        break;
                    }
                }
            }
            cache.put(token.getLemma(), numSentences);
            return numSentences;
        }
    }

    /**
     * return a value for each sentence on how it scores with the term/frequency - inverse sentence frequency
     * @param sentenceList the sentences to score
     * @param wordCount the frequency map of all words
     * @return the scores for the sentences
     */
    private List<Float> getTfIsf(List<Sentence> sentenceList, Map<String, Integer> wordCount) {
        List<Float> sentenceFeatures = new ArrayList<>();
        Map<String, Integer> cache = new HashMap<>();
        float largest = 0.0f;
        for (Sentence sentence : sentenceList) {
            float w = 0.0f;
            for (Token token : sentence.getTokenList()) {
                double n = getWordSentenceCount(sentenceList, token, cache);
                w += (double)wordCount.get(token.getLemma()) * Math.log(sentenceList.size() / n);
            }
            sentenceFeatures.add(w);
            if (w > largest) {
                largest = w;
            }
        }
        // normalize?
        if (largest > 0.0f) {
            List<Float> finalSentenceFeatures = new ArrayList<>();
            for (float value : sentenceFeatures) {
                finalSentenceFeatures.add(value / largest);
            }
            return finalSentenceFeatures;
        }
        return sentenceFeatures;
    }


    /**
     * rank the numToRank sentence with a score decending from 1.0 to 0.0
     * @param sentenceList the list of sentences
     * @param numToRank how many to "rank" (default was 5)
     * @return the list of features for these sentences
     */
    private List<Float> getSentencePositionRating(List<Sentence> sentenceList, int numToRank) {
        List<Float> sentenceFeatures = new ArrayList<>();
        float n = numToRank;
        for (Sentence sentence : sentenceList) {
            if (n > 0.0f) {
                sentenceFeatures.add(n / numToRank);
                n -= 1.0f;
            } else {
                sentenceFeatures.add(0.0f);
            }
        }
        return sentenceFeatures;
    }


}

