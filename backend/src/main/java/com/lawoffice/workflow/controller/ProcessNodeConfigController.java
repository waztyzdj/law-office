package com.lawoffice.workflow.controller;

import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.controller.BaseController;
import com.lawoffice.workflow.entity.ProcessNodeConfig;
import com.lawoffice.workflow.req.ProcessNodeConfigReq;
import com.lawoffice.workflow.service.IProcessNodeConfigService;
import com.lawoffice.workflow.vo.ProcessNodeConfigVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workflow/admin/node")
@Tag(name = "流程节点配置", description = "审批中心流程节点配置管理")
@ModuleInfo(value = "workflow:process", name = "流程节点配置", description = "流程设计内部节点配置能力")
public class ProcessNodeConfigController extends BaseController<IProcessNodeConfigService, ProcessNodeConfig, ProcessNodeConfigVO, ProcessNodeConfigReq> {

    @Autowired
    public ProcessNodeConfigController(IProcessNodeConfigService service) {
        this.baseService = service;
    }
}
