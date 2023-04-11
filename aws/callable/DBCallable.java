package com.freecharge.smsprofilerservice.aws.callable;

import com.freecharge.smsprofilerservice.dao.mysql.model.TemplateModel;
import com.freecharge.smsprofilerservice.dao.mysql.model.UntrainedTemplateSimilarityModel;
import com.freecharge.smsprofilerservice.dao.mysql.service.UntrainedTemplateSimilarityDao;
import com.freecharge.smsprofilerservice.stringsimilarity.algo.StringSimilarity;
import com.freecharge.smsprofilerservice.stringsimilarity.executor.StringSimilarityExecutor;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.Callable;

@Slf4j
@Data
public class DBCallable implements Callable<String> {

    final List<TemplateModel> untrainedModelsWithHighCount;

    final List<TemplateModel> untrainedModelsWithLowCount;

    final UntrainedTemplateSimilarityDao untrainedTemplateSimilarityDao;

    final StringSimilarity stringSimilarity;

    final Double stringSimilarityPassValue;

    public DBCallable(@NonNull final UntrainedTemplateSimilarityDao untrainedTemplateSimilarityDao,
                      @NonNull final List<TemplateModel> untrainedModelsWithLowCount,
                      @NonNull final List<TemplateModel> untrainedModelsWithHighCount,
                      @NonNull final StringSimilarity stringSimilarity,
                      @NonNull final Double stringSimilarityPassValue) {
       this.untrainedTemplateSimilarityDao = untrainedTemplateSimilarityDao;
       this.untrainedModelsWithHighCount = untrainedModelsWithHighCount;
       this.untrainedModelsWithLowCount = untrainedModelsWithLowCount;
       this.stringSimilarity= stringSimilarity;
       this.stringSimilarityPassValue = stringSimilarityPassValue;
    }

    @Override
    public String call() throws Exception {
        log.debug("call method execution");
        final Long start = System.currentTimeMillis();
        untrainedModelsWithLowCount.forEach(templateModel -> {
            addIntoTableIfMatched(untrainedModelsWithHighCount, templateModel);
        });
        final Long end = System.currentTimeMillis();
        log.info("Time taken to upload the records {}, ", end-start);
        return "200-OK";
    }

    private void saveDataIntoDB(@NonNull final TemplateModel templateModel,
                                @NonNull final TemplateModel matchedTemplateModel){
        untrainedTemplateSimilarityDao.save(UntrainedTemplateSimilarityModel.builder()
                .diverged(false)
                .matched_template_message(matchedTemplateModel.getTemplateMessage())
                .matchedHashcode(matchedTemplateModel.getHashcode())
                .parentHashcode(templateModel.getHashcode())
                .parent_template_message(templateModel.getTemplateMessage())
                .build());
    }

    private void addIntoTableIfMatched(@NonNull final List<TemplateModel> untrainedModelsWithHighCount,
                                       @NonNull final TemplateModel templateModel) {
        final StringSimilarityExecutor executor = StringSimilarityExecutor
                .StringSimilarityExecutors.getStringSimilarityExecutor(stringSimilarity);
        for (TemplateModel model : untrainedModelsWithHighCount) {
            final double match = executor.runStringSimilarity(templateModel.getTemplateMessage(), model.getTemplateMessage());
            if (match * 100 >= stringSimilarityPassValue) {
                saveDataIntoDB(templateModel, model);
                break;
            }
        }
    }
}
