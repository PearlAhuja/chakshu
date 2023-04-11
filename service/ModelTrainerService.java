package com.freecharge.smsprofilerservice.service;

import java.util.List;

public interface ModelTrainerService {
    byte[] train(String filePrefix, List<String> strings);
}