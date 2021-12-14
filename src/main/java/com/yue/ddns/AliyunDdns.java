package com.yue.ddns;

import com.alibaba.fastjson.JSON;
import com.aliyun.rds20140815.Client;
import com.aliyun.rds20140815.models.ModifySecurityIpsRequest;
import com.aliyun.rds20140815.models.ModifySecurityIpsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.alidns.model.v20150109.DescribeSubDomainRecordsRequest;
import com.aliyuncs.alidns.model.v20150109.DescribeSubDomainRecordsResponse;
import com.aliyuncs.alidns.model.v20150109.UpdateDomainRecordRequest;
import com.aliyuncs.alidns.model.v20150109.UpdateDomainRecordResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AliyunDdns {

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	@Value("${spring.profiles.active}")
	private String activeProfile;// 环境

	private DefaultProfile profile;
	private IAcsClient ddnsClient;
	private Client rdsClient;

	@Value("${aliyun.ddns.regionId}")
	private String regionId;// 地域ID
	@Value("${aliyun.ddns.accessKeyId}")
	private String ddsnAccessKeyId;// 您的AccessKey ID
	@Value("${aliyun.ddns.secret}")
	private String ddnsSecret;// 您的AccessKey Secret
    @Value("${aliyun.ddns.domains}")
	private String domains;
    @Value("${aliyun.ddns.fixedDelay}")
    private String fixedDelay;

	@Value("${aliyun.rds.accessKeyId}")
	private String rdsAccessKeyId;
	@Value("${aliyun.rds.secret}")
	private String rdsSecret;

	@PostConstruct
	public void init() throws Exception {
		//  设置鉴权参数，初始化客户端
		logger.info("----------------ddns init start----------------");
		profile = DefaultProfile.getProfile(regionId, ddsnAccessKeyId, ddnsSecret);
		ddnsClient = new DefaultAcsClient(profile);
		logger.info("需要监控的domains：{}", domains);
		logger.info("任务执行频率：{}s", fixedDelay);
		logger.info("----------------ddns init end----------------");

		logger.info("----------------rds init start----------------");
		Config config = new Config().setAccessKeyId(rdsAccessKeyId)
				.setAccessKeySecret(rdsSecret)
				.setEndpoint("rds.aliyuncs.com");
		rdsClient = new Client(config);
	logger.info("----------------rds init end----------------");
	}

	@Scheduled(initialDelay = 10000, fixedDelayString = "${aliyun.ddns.fixedDelay}000")//3min
	public void schedulingMethod() {
	    if (!StringUtils.hasText(domains)) {
	        logger.info("无需要监控的domain记录");
	        return;
        }
        String[] domainList = domains.split(",");//空字符串split后会得到[""]
        for (String domain : domainList) {
			logger.info("开始处理：domain:{}", domain);
            //指定查询的二级域名
            DescribeSubDomainRecordsRequest recordsRequest = new DescribeSubDomainRecordsRequest();
            recordsRequest.setSubDomain(domain);
            DescribeSubDomainRecordsResponse recordsResponse = this.describeSubDomainRecords(recordsRequest, ddnsClient);
            List<DescribeSubDomainRecordsResponse.Record> domainRecords = recordsResponse.getDomainRecords();
            logger.info("查询解析记录结果，domainRecords:{}", JSON.toJSONString(domainRecords));
            //最新的一条解析记录
            if (domainRecords.size() != 0) {
                DescribeSubDomainRecordsResponse.Record record = domainRecords.get(0);
                //  记录ID
                String recordId = record.getRecordId();
                //  记录值
                String recordsValue = record.getValue();
                //  当前主机公网IP
                String currentHostIP = this.getCurrentHostIP();
                logger.info("当前主机公网IP为：{},当前记录值为：{}", currentHostIP, recordsValue);

                if ("dev".equals(activeProfile)) {
                    logger.info("{}环境不修改",activeProfile);
                    continue;
                }
                if (!currentHostIP.equals(recordsValue)) {
                    //  修改解析记录
                    UpdateDomainRecordRequest updateDomainRecordRequest = new UpdateDomainRecordRequest();
                    //  主机记录
                    updateDomainRecordRequest.setRR(domain.substring(0,domain.indexOf(".")));
                    //  记录ID
                    updateDomainRecordRequest.setRecordId(recordId);
                    //  将主机记录值改为当前主机IP
                    updateDomainRecordRequest.setValue(currentHostIP);
                    //  解析记录类型
                    updateDomainRecordRequest.setType("A");
                    UpdateDomainRecordResponse updateDomainRecordResponse = this.updateDomainRecord(updateDomainRecordRequest, ddnsClient);
                    logger.info("updateDomainRecord:{}", JSON.toJSONString(updateDomainRecordResponse));
                    modifySecurityIps(currentHostIP);// TODO: 2021/12/14 rds不用了，可以去掉了
                }
            }
        }

	}

	/**
	 * 获取主域名的所有解析记录列表
	 */
	private DescribeSubDomainRecordsResponse describeSubDomainRecords(DescribeSubDomainRecordsRequest request, IAcsClient client) {
		try {
			// 调用SDK发送请求
			return client.getAcsResponse(request);
		} catch (ClientException e) {
			e.printStackTrace();
			// 发生调用错误，抛出运行时异常
			throw new RuntimeException();
		}
	}

	/**
	 * 获取当前主机公网IP
	 */
	private String getCurrentHostIP() {
		// 这里使用jsonip.com第三方接口获取本地IP
		String jsonip = "https://jsonip.com";
		// 接口返回结果
		StringBuffer result =  new StringBuffer();
		BufferedReader in = null;
		try {
			// 使用HttpURLConnection网络请求第三方接口
			URL url = new URL(jsonip);
			HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
			urlConnection.setRequestMethod("GET");
			urlConnection.setReadTimeout(10000);//设置超时时间、避免定时任务线程hang住
			urlConnection.connect();
			in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
			String line;
			while ((line = in.readLine()) != null) {
				result.append(line);
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("获取当前ip出错，error message:{}",e.getMessage());
		}
		// 使用finally块来关闭输入流
		finally {
			try {
				if (in != null) {
					in.close();
				}
			} catch (Exception e2) {
				e2.printStackTrace();
			}
		}
		//  正则表达式，提取xxx.xxx.xxx.xxx，将IP地址从接口返回结果中提取出来
		String rexp = "(\\d{1,3}\\.){3}\\d{1,3}";
		Pattern pat = Pattern.compile(rexp);
		Matcher mat = pat.matcher(result.toString());
		String res = "";
		while (mat.find()) {
			res = mat.group();
			break;
		}
		return res;
	}

	/**
	 * 修改解析记录
	 */
	private UpdateDomainRecordResponse updateDomainRecord(UpdateDomainRecordRequest request, IAcsClient client) {
		try {
			//  调用SDK发送请求
			return client.getAcsResponse(request);
		} catch (ClientException e) {
			e.printStackTrace();
			//  发生调用错误，抛出运行时异常
			throw new RuntimeException(e.getMessage());
		}
	}

	private void modifySecurityIps(String securityIp) {
		try {
			logger.info("修改rds白名单");
			ModifySecurityIpsRequest modifySecurityIpsRequest = new ModifySecurityIpsRequest().setDBInstanceId("rm-2zexsrx1445g72i8o")
					.setSecurityIps(securityIp)
					.setDBInstanceIPArrayName("niutuo");
			ModifySecurityIpsResponse modifySecurityIpsResponse = rdsClient.modifySecurityIps(modifySecurityIpsRequest);
			logger.info("修改rds白名单完成，result：{}", modifySecurityIpsResponse);
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("修改rds白名单失败，error msg：{}", e.getMessage());
		}
	}
}