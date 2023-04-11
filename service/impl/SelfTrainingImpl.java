package com.freecharge.smsprofilerservice.service.impl;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class SelfTrainingImpl {

    public String selfTrainText(String trainedText, String untrainedText) {
        try {
            List<NERToken> tokenList = getNerTokens(trainedText);
            ArrayList<String> untrainedTokens = getUntrainedTextList(untrainedText);
            int count = 0;
            if (!tokenList.isEmpty()) {
                for (NERToken nerToken : tokenList) {
                    int index = nerToken.getIndex();
                    String value = nerToken.getValue();
                    untrainedTokens.add(index + count, "<START:" + value + ">");
                    untrainedTokens.add(index + 2 + count, "<END>");
                    count += 2;
                }
                return String.join(" ", untrainedTokens).trim();
            }
        }catch (Exception e){
         log.error("Error with self training to message : {} by : {}",untrainedText,trainedText);
        }
        return null;
    }

    private ArrayList<String> getUntrainedTextList(String untrainedText) {
        ArrayList<String> untrainedTokens = new ArrayList<>();
        for (String s : untrainedText.split(" ")) {
            untrainedTokens.add(s);
        }
        return untrainedTokens;
    }

    public List<NERToken> getNerTokens(String trainedText) {
        List<NERToken> tokenList = new ArrayList<>();
        String[] trainedTokens = trainedText.split(" ");
        int count = 0;
        for (int i = 0; i < trainedTokens.length; i++) {
            String subText = trainedTokens[i];
            if (subText.startsWith("<START:")) {
                tokenList.add(new NERToken(i - count, subText.substring(7, subText.length() - 1)));
                count += 2;
            }
        }
        return tokenList;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class NERToken {
        private int index;
        private String value;
    }

    private static void main(String[] args) {
        new SelfTrainingImpl().selfTrainText("Dear <START:uan> 100002352343, <END> your passbook balance against <START:pfnumber> GNGGN1303472328 <END> is Rs <START:pfbalance> 100000/- <END> . Contribution of Rs <START:monthlyPfContribution> 1000/- <END> for due month <START:pfMonth> 052020 <END> has been received.","Dear 101255501340, your passbook balance against PBCHD00388900000011101 is Rs 69444/-. Contribution of Rs 3910/- for due month 052020 has been received.");
    }
}
