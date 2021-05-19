package com.yue.ddns;

import com.alibaba.fastjson.JSON;
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
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AliyunDdns {

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	private DefaultProfile profile;
	private IAcsClient client;
	@Value("${spring.profiles.active}")
	private String activeProfile;// 环境

	@Value("${aliyun.ddns.regionId}")
	private String regionId;// 地域ID
	@Value("${aliyun.ddns.accessKeyId}")
	private String accessKeyId;// 您的AccessKey ID
	@Value("${aliyun.ddns.secret}")
	private String secret;// 您的AccessKey Secret
    @Value("${aliyun.ddns.domains}")
	private String domains;
    @Value("${aliyun.ddns.fixedDelay}")
    private String fixedDelay;

	@PostConstruct
	public void init(){
		//  设置鉴权参数，初始化客户端
        logger.info("----------------system init start----------------");
		profile = DefaultProfile.getProfile(regionId, accessKeyId, secret);
		client = new DefaultAcsClient(profile);
		logger.info("需要监控的domains：{}", domains);
		logger.info("任务执行频率：{}s", fixedDelay);
        logger.info("----------------system init end----------------");

    }

	@Scheduled(initialDelay = 10000, fixedDelayString = "${aliyun.ddns.fixedDelay}000")//3min
	public void schedulingMethod() {
	    if (!StringUtils.hasText(domains)) {
	        logger.info("无需要监控的domain记录");
	        return;
        }
        String[] domainList = domains.split(",");//空字符串split后会得到[""]
        for (String domain : domainList) {
            //指定查询的二级域名
            DescribeSubDomainRecordsRequest recordsRequest = new DescribeSubDomainRecordsRequest();
            recordsRequest.setSubDomain(domain);
            DescribeSubDomainRecordsResponse recordsResponse = this.describeSubDomainRecords(recordsRequest, client);
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
                    updateDomainRecordRequest.setRR("cloud");
                    //  记录ID
                    updateDomainRecordRequest.setRecordId(recordId);
                    //  将主机记录值改为当前主机IP
                    updateDomainRecordRequest.setValue(currentHostIP);
                    //  解析记录类型
                    updateDomainRecordRequest.setType("A");
                    UpdateDomainRecordResponse updateDomainRecordResponse = this.updateDomainRecord(updateDomainRecordRequest, client);
                    logger.info("updateDomainRecord:{}", JSON.toJSONString(updateDomainRecordResponse));
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
		String result = "";
		BufferedReader in = null;
		try {
			// 使用HttpURLConnection网络请求第三方接口
			URL url = new URL(jsonip);
			HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
			urlConnection.setRequestMethod("GET");
			urlConnection.connect();
			in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
			String line;
			while ((line = in.readLine()) != null) {
				result += line;
			}
		} catch (Exception e) {
			e.printStackTrace();
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
		Matcher mat = pat.matcher(result);
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
}