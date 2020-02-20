package org.jeecg.modules.persona.hjrk.entity;

import java.io.Serializable;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;
import org.jeecgframework.poi.excel.annotation.Excel;

/**
 * @Description: 综治人口
 * @Author: jeecg-boot
 * @Date:   2020-02-20
 * @Version: V1.0
 */
@Data
@TableName("zz_persona")
public class ZzPersona implements Serializable {
    private static final long serialVersionUID = 1L;
    
	/**主键*/
	@TableId(type = IdType.ID_WORKER_STR)
	private java.lang.String id;
	/**创建人*/
	@Excel(name = "创建人", width = 15)
	private java.lang.String createBy;
	/**更新人*/
	@Excel(name = "更新人", width = 15)
	private java.lang.String updateBy;
	/**所属部门*/
	@Excel(name = "所属部门", width = 15)
	private java.lang.String sysOrgCode;
	/**类型*/
	@Excel(name = "类型", width = 15)
	private java.lang.String type;
	/**外表*/
	@Excel(name = "外表", width = 15)
	private java.lang.String outTable;
	/**外表id*/
	private java.lang.String outId;
	/**姓名*/
	@Excel(name = "姓名", width = 15)
	private java.lang.String name;
	/**身份证*/
	@Excel(name = "身份证", width = 15)
	private java.lang.String sfz;
	/**曾用名*/
	@Excel(name = "曾用名", width = 15)
	private java.lang.String oldName;
	/**性别*/
	@Excel(name = "性别", width = 15)
	private java.lang.String sex;
	/**照片*/
	@Excel(name = "照片", width = 15)
	private java.lang.String photo;
	/**出生日期*/
	@Excel(name = "出生日期", width = 15, format = "yyyy-MM-dd")
	@JsonFormat(timezone = "GMT+8",pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern="yyyy-MM-dd")
	private java.util.Date birthday;
	/**民族*/
	@Excel(name = "民族", width = 15)
	private java.lang.String zm;
	/**籍贯*/
	@Excel(name = "籍贯", width = 15)
	private java.lang.String jg;
	/**婚姻状况*/
	@Excel(name = "婚姻状况", width = 15)
	private java.lang.String hyzk;
	/**政治面貌*/
	@Excel(name = "政治面貌", width = 15)
	private java.lang.String zzmm;
	/**学历*/
	@Excel(name = "学历", width = 15)
	private java.lang.String xl;
	/**宗教信仰*/
	@Excel(name = "宗教信仰", width = 15)
	private java.lang.String zjxy;
	/**职业类别*/
	@Excel(name = "职业类别", width = 15)
	private java.lang.String zylb;
	/**职业*/
	@Excel(name = "职业", width = 15)
	private java.lang.String zy;
	/**户籍门（楼）祥*/
	@Excel(name = "户籍门（楼）祥", width = 15)
	private java.lang.String hjxq;
	/**现住门（楼）祥*/
	@Excel(name = "现住门（楼）祥", width = 15)
	private java.lang.String xzdxq;
	/**联系方式*/
	@Excel(name = "联系方式", width = 15)
	private java.lang.String lxfs;
}
