package com.courtflow.homework.common.dto.request;

import lombok.Data;

@Data
public class PageQueryRequest {

    private int pageNumber = 1;
    private int pageSize = 10;

}
