/*
 * Copyright 2016 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.example.bot.spring.echo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.linecorp.bot.model.event.Event;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;

import java.io.*;
import java.util.*;

import javax.net.ssl.HttpsURLConnection;
import java.net.*;
import org.jsoup.*;

import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;


@SpringBootApplication
@LineMessageHandler
public class EchoApplication {
    private final Logger log = LoggerFactory.getLogger(EchoApplication.class);
    static Tokenizer tokenizer = new Tokenizer();
    public static void main(String[] args) {
        SpringApplication.run(EchoApplication.class, args);
    }

    @EventMapping
    public Message handleTextMessageEvent(MessageEvent<TextMessageContent> event) {
        log.info("event: " + event);
        final String originalMessageText = generateResponse(event.getMessage().getText());
        return new TextMessage(originalMessageText);
    }

    @EventMapping
    public void handleDefaultMessageEvent(Event event) {
        System.out.println("event: " + event);
    }

    public static String generateResponse(String userInput) {
		String responce = "";
		ArrayList<String> names = new ArrayList<>();
		ArrayList<String> save = new ArrayList<>();
		List<Token> tokens = tokenizer.tokenize(userInput);
		String before = "";
		for(Token token: tokens) {
			String word = token.getPartOfSpeechLevel1();
			if(word.equals("名詞")) {
				if(before.equals("名詞")) {
					names.set(names.size() - 1, names.get(names.size() - 1) + token.getSurface());
					save.add(names.get(names.size() - 1));
					save.add(token.getSurface());
				}
				else {
					names.add(token.getSurface());
				}
			}
			before = word;
		}
		String A = names.get(0);
		String B = names.get(1);
		List<String> proplds = getWikidataProplds(B);
		String wdJson = getWikidataJson(A);
		Map<String, Object> wdMap = json2Map(wdJson);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> list = (List<Map<String, Object>>)wdMap.get("result");
		if(list.size()==0) {
			return A + "の意味が分かりません。";
		}
		else if(proplds.size()==0) {
			return generateTrivia(A, B, list);
		}
		List<String> propvals = getPropVals(list.get(0), proplds.get(0));
		if(propvals.size() == 0) {
			return generateTrivia(A, B, list);
		}
		responce = getLabelById(getWikidataIds(A).get(0)) + "の" + getLabelById(proplds.get(0)) + "は";
		for(int i=0; i<propvals.size()-1; i++) {
			responce += propvals.get(i) + "、";
		}
		responce += propvals.get(propvals.size()-1) + "です。";
		return responce;
	}
	
	/**
	 * 
	 * @param res
	 * @return
	 */
	public static String getEntityID(Map<String, Object> res) {
		return (String)((Map)res.get("entities")).keySet().iterator().next();
	}
	
	/**
	 * 
	 * @param map
	 * @param prop
	 * @return
	 */
	public static List<String> getPropVals(Map<String, Object> res, String prop) {
		List<String> vals = new ArrayList<String>();
		String entityID = getEntityID(res);
		@SuppressWarnings("unchecked")
		Map<String, Object> entityMap = (Map<String, Object>)((Map<String, Object>)res.get("entities")).get(entityID);
		@SuppressWarnings("unchecked")
		Map<String, Object> claimMap = (Map<String, Object>)entityMap.get("claims");
		if (claimMap != null) {	
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> propList = (List<Map<String, Object>>)claimMap.get(prop);
			if (propList != null) {
				for (Map<String, Object> propMap: propList) {
					@SuppressWarnings("unchecked")
					Map<String,Object> valMap = (Map<String, Object>)((Map<String, Object>)propMap.get("mainsnak")).get("datavalue");
					Object val = valMap.get("value");
					if(val instanceof String) {
						vals.add((String)val);
					}else if(val instanceof Map) {
						@SuppressWarnings("unchecked")
						Map<String, Object> map = (Map<String, Object>)val;
						String id = (String)map.get("id");
						if(id == null) {
							String amount = (String)map.get("amount");
							vals.add(amount);
							return vals;
						}
						String jaLabel = getLabelById(id);
						vals.add(jaLabel);
					}else {
						vals.add(val.toString());
					}
					
				}
			}
		}	
		return vals;
	}
	
	
	/**
	 * Wikidataからデータを検索
	 * @param query
	 * @return　Wikidataから取得したJSON文字列
	 */
	public static String getWikidataJson(String query) {
		StringBuffer sb = new StringBuffer();
		sb.append("{\"result\":[");
		int initLen = sb.length();
		List<String> ids = getWikidataIds(query);
		List<String> proplds = getWikidataProplds(query); //プロパティに対応
		if(proplds.contains(query)) ids = proplds;
		for (String id: ids) {
			if (sb.length() > initLen) {
				sb.append(",");
			}
			String url = "https://www.wikidata.org/wiki/Special:EntityData/" + id + ".json";
			String json = getData(url);
			sb.append(getData(url));
		}
		sb.append("]}");
		return sb.toString();
	}
	
	/**
	 * WikidataエンティティのIDを検索
	 * @param query
	 * @return WikidataエンティティのIDのリスト
	 */
	public static List<String> getWikidataIds(String query) {
		String encodedQuery = "";
		try {
			encodedQuery = URLEncoder.encode(query, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}	
		String url = "https://www.wikidata.org/w/api.php?action=wbsearchentities&language=ja&format=json"
		              + "&search=" + encodedQuery;
		@SuppressWarnings("unchecked")
		Map<String, Object> map = json2Map(getData(url));
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> list = (List<Map<String, Object>>)map.get("search");
		List<String> ids = new ArrayList<String>();
		for (Map<String, Object> entMap: list) {
			String id = (String)entMap.get("id");
			ids.add(id);
		}
		return ids;
	}
	public static List<String> getWikidataProplds(String query) {
		String encodedQuery = "";
		try {
			encodedQuery = URLEncoder.encode(query, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}	
		String url = "https://www.wikidata.org/w/api.php?action=wbsearchentities&language=ja&format=json&type=property"
		              + "&search=" + encodedQuery;
		@SuppressWarnings("unchecked")
		Map<String, Object> map = json2Map(getData(url));
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> list = (List<Map<String, Object>>)map.get("search");
		List<String> proplds = new ArrayList<String>();
		for (Map<String, Object> entMap: list) {
			String propld = (String)entMap.get("id");
			proplds.add(propld);
		}
		return proplds;
	}
	
	/**
	 * JSON形式の文字列をMapに変換
	 * @param json
	 * @return JSONから変換したMapオブジェクト
	 */
	public static Map<String, Object> json2Map(String json) {
		ObjectMapper mapper = new ObjectMapper();
		Map<String, Object> map = new HashMap<String, Object>();
		map = null;
		try {
			map = mapper.readValue(json, new TypeReference<Map<String, Object>>(){});
		} catch (Exception e) {
			e.printStackTrace();
		}
		return map;
	}
	
	
	/**
	 * HTMLに限らない形式のデータをWebから取得
	 * @param url
	 * @return 返ってきたデータ
	 */
    public static String getData(String url) {
    	String enc = "UTF-8";
    	StringBuffer sb = new StringBuffer();
    	try {
    		BufferedReader in = null;
    		if (url.startsWith("https")) {
    			HttpsURLConnection conn = (HttpsURLConnection)new URL(url).openConnection();
    			in = new BufferedReader(new InputStreamReader(conn.getInputStream(), enc));
    		} else {
    			URLConnection conn = new URL(url).openConnection();
    			in = new BufferedReader(new InputStreamReader(conn.getInputStream(), enc));
    		}
    		for (String line = in.readLine(); line != null; line = in.readLine()) {
    			sb.append(line);
    			sb.append("\n");
    		}
    	} catch (IOException e) {
    		e.printStackTrace();
    	}
    	return sb.toString();
    }
    
    public static String getLabelById(String id) {
    	String jalabel = "";
    	String json = getWikidataJson(id);
    	Map<String, Object> map = json2Map(json);
		@SuppressWarnings("unchecked")
    	List<Map<String, Object>> list = (List<Map<String, Object>>)map.get("result");
		@SuppressWarnings("unchecked")
		Map<String, Object> entityMap = (Map<String, Object>)((Map<String, Object>)list.get(0).get("entities")).get(id);
		@SuppressWarnings("unchecked")
		Map<String, Object> labelMap = (Map<String, Object>)entityMap.get("labels");
		Map<String, Object> jaMap = (Map<String, Object>)labelMap.get("ja");
		if(jaMap == null) {
			List<String> labelset = new ArrayList<String>(labelMap.keySet());
			String language = labelset.get(rand.nextInt(labelset.size()));
			String label = (String)((Map)labelMap.get(language)).get("value");
			return label + "(" + language + ")";
		}
		jalabel = (String)jaMap.get("value");
		return jalabel;
    }
    
    public static List<String> getPropIds(Map<String, Object> res) {
    	List<String> propids = new ArrayList<String>();
    	List<String> vals = new ArrayList<String>();
		String entityID = getEntityID(res);
		@SuppressWarnings("unchecked")
		Map<String, Object> entityMap = (Map<String, Object>)((Map<String, Object>)res.get("entities")).get(entityID);
		@SuppressWarnings("unchecked")
		Map<String, Object> claimMap = (Map<String, Object>)entityMap.get("claims");
		if (claimMap != null) {	
			propids = new ArrayList<>(claimMap.keySet());
		}
		return propids;
    }
    
    public static String generateTrivia(String A, String B, List<Map<String, Object>> list) {
    	String responce = "";
    	Random rand = new Random();
		List<String> trivia = getPropIds(list.get(0));
		responce = getLabelById(getWikidataIds(A).get(0)) + "の" + B + "は分かりませんが、" + A + "の";
		List<String> trivals = new ArrayList<String>();
		while(true) {
			int num = rand.nextInt(trivia.size());
			String trivium = trivia.get(num);
			try {
				trivals = getPropVals(list.get(0), trivium);
				responce += getLabelById(trivium) + "は";
				break;
			}
			catch(Exception e){}
		}
		for(int i=0; i<trivals.size()-1; i++) {
			responce += trivals.get(i) + "、";
		}
		responce += trivals.get(trivals.size()-1) + "です。";
		return responce;
    }
}
