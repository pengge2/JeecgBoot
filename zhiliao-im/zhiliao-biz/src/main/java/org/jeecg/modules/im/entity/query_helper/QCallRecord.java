package org.jeecg.modules.im.entity.query_helper;


import lombok.Data;
import org.jeecg.modules.im.entity.CallRecord;

@Data
public class QCallRecord extends CallRecord {
    private String sender;
    private String receiver;
}
