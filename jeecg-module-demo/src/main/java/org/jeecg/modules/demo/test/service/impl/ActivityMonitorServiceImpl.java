package org.jeecg.modules.demo.test.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.modules.demo.test.entity.ActivityCase;
import org.jeecg.modules.demo.test.entity.ActivityMonitor;
import org.jeecg.modules.demo.test.mapper.ActivityCaseMapper;
import org.jeecg.modules.demo.test.mapper.ActivityMonitorMapper;
import org.jeecg.modules.demo.test.service.IActivityCaseService;
import org.jeecg.modules.demo.test.service.IActivityMonitorService;
import org.springframework.stereotype.Service;

@Service
public class ActivityMonitorServiceImpl extends ServiceImpl<ActivityMonitorMapper, ActivityMonitor>  implements IActivityMonitorService {

    @Override
    public void reset(String activityId) {
        getBaseMapper().reset(activityId);
    }
}
