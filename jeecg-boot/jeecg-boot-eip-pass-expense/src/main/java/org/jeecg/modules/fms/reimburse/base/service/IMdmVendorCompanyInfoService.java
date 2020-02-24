package org.jeecg.modules.fms.reimburse.base.service;

import org.jeecg.modules.fms.reimburse.base.entity.MdmVendorCompanyInfo;
import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;

/**
 * @Description: 主数据供应商归属组织
 * @Author: jeecg-boot
 * @Date:   2020-01-17
 * @Version: V1.0
 */
public interface IMdmVendorCompanyInfoService extends IService<MdmVendorCompanyInfo> {

	public List<MdmVendorCompanyInfo> selectByMainId(String mainId);
}