package kr.co.cashq.order_send;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
//import com.nostech.safen.SafeNo;

/**

 * Order_fmd_queue 테이블 관련 객체
 * @author 문태부.
 * @date : 2019-05-10 오후 8:29:04
 *  @param['url']="http://cashq.co.kr/ 
 *   목적 : https://github.com/Taebu/order_send/issues/1
 *   이슈를 처리하기 위한 프로젝트 이며 5분(300초) 이상이 대기 중인 프로세스가 여전히 대기 중일때 상태를 변경하고 회원 정보를 조회하여 fcm을 전송한다.
 *    
 *  
 */
public class Order_fcm_queue {
	
	/**
	 * safen_cmd_queue 테이블의 데이터를 처리하기 위한 주요한 처리를 수행한다.
	 * 
	 */
	
	public static void doMainProcess() {
		Connection con = DBConn.getConnection();
		String mb_hp="";
		String pay_status="";
		String Tradeid="";
		String seq="0";
		String st_seq="0";
		String bs_code="";
		String mb_address="";
		//set_kgorder_autocancel_from_url(String tradeid, String mobilid,String prdtprice
		String mobilid="";
		String prdtprice="";
		
		TimerMachine tm = new TimerMachine();
		/* 배열에 들어간것은 끝난 것으로 해서 프로그램을 프로그램 루프 상 제외 문제일 경우 예외 처리하는 프로세스를 띄운다. */
		final String[] VALUES = new String[] {"pay_complete","pay_real_card","pay_real_cash"};
		
		/* 가맹점 정보 */
		Map<String, String> store_info = new HashMap<String, String>();

		/* 주문 정보 */
		Map<String, String> order_info = new HashMap<String, String>();

		
		/* 푸시 정보 */
		Map<String, String> push_info = new HashMap<String, String>();
		String messages="";
		String[] regex_rule;
		String[] regex_array;
		int eventcnt = 0;
		String machine_number1_order_number = "";
		String machine_number2_order_number = "";
		String machine_number3_order_number = "";
		String machine_number4_order_number = "";
		
		/* 포인트 갯수를 센다. */
		int point_count= 0;
		
		/* 핸드폰인지 여부 */
		boolean is_hp = false;
		
		/* GCM 전송 성공 여부 */
		boolean success_gcm = false;
		
		/* ATA 전송 성공 여부 */
		boolean success_ata = false;
		 
		/* SMS 전송 성공 여부 */
		boolean success_sms = false;
		
		
		/* 주문을 받았나? */
		boolean did_set_order = false;
		
		/* 비즈톡에 입력된 값 */
		int wr_idx=0;	
		if (con != null) {
			MyDataObject dao = new MyDataObject();
			StringBuilder sb = new StringBuilder();
			
			/*********************************************************
			 * 1. 아래 조건을 만족하는 `cashq`.`ordtake` 테이블을 조회한다.  
			 * 	조건 1) 결제완료(pc : pay_complete,  fpw : field pay wait)를  조회 한다..
			 *   - pay_status in ('pc', 'fpw')
			 * 조건 2) 주문 선택을 하지 않은 건을 조회 한다.
			 *  -  exam_num1='0' 
			 * 조건 3) 결제가 이루어진 시점을 기준으로 5분이 지난 건을 조회 한다.
			 *  - date_add(order_date,interval 5 minute)>now() 
			 *********************************************************/
					
			sb.append("select * from ordtake where 1=1 ");
			sb.append(" and  pay_status in ('pc', 'fpw') ");
			sb.append(" and exam_num1='0' ");
			sb.append(" and date_add(insdate,interval 5 minute)>now() ");
			sb.append(" ;");
			
			/*
			 * try  SQLException e,Exception e
			 * finally dao.closePstmt();
			   
  		     * */
			try {
				dao.openPstmt(sb.toString());
				dao.setRs(dao.pstmt().executeQuery());

				/* 2. 값이 있으면 */
			   while(dao.rs().next()) {
					ORDER_SEND.heart_beat = 1;
					
					/* resultSet 을 Map<string,String>형태로 변환 한다. */
					order_info = getResultMapRows(dao.rs());
					
					/* 배달주문의 고유 번호(seq)를 불러 옵니다. */
					
					seq=dao.rs().getString("seq");
					st_seq=dao.rs().getString("st_seq");
	
					mb_hp=dao.rs().getString("mb_hp");
					Tradeid=dao.rs().getString("Tradeid");
					pay_status=dao.rs().getString("pay_status");
					mobilid =get_mobilid(Tradeid);
					
					prdtprice = dao.rs().getString("appamount");
					
					/*상점아이디를 조회한다. select * from store where seq=? */
					store_info=get_store_info(st_seq);
					
					/* +82 0 - 문자를 제거합니다. */
					mb_hp=mb_hp.replaceAll("\\-", "/").replaceAll("\\+82", "0").trim();
					
					/* 핸드폰인지 구분한다. */
					is_hp=isCellphone(mb_hp);
					
					
					// TimerMachine 구현  
					
					boolean in_order_number = ORDER_SEND.check_order_number.contains(seq);
					
					/* 첫번째 머신이 구동하지 않았고 해당 주문이 추가 되지 않았다면 첫번째 timer_machine을 구동합니다.*/
					if(!ORDER_SEND.hasStarted1&&!in_order_number){
						
						/* arraylist 에 "해당 주문 번호"(ordtake.seq) 추가 한다. */
						ORDER_SEND.check_order_number.add(seq);
						
						/* 첫번째  머신이 실행되었다고 선언한다. */
						ORDER_SEND.hasStarted1 = true;
						
						/* 첫번째  머신을 구동한다. */
						tm.timer_machine1(seq,Tradeid,mobilid,prdtprice);
					/* 두번째 머신이 구동하지 않았고 해당 주문이 추가 되지 않았다면 두번째 timer_machine을 구동합니다.*/
					}else if(!ORDER_SEND.hasStarted2&&!in_order_number){
						
						/* arraylist 에 "해당 주문 번호"(ordtake.seq) 추가 한다. */
						ORDER_SEND.check_order_number.add(seq);
						
						/* 두번째  머신이 실행되었다고 선언한다. */
						ORDER_SEND.hasStarted2 = true;
						
						/*두번째  머신을 구동한다. */
						tm.timer_machine2(seq,Tradeid,mobilid,prdtprice);

					/* 세번째 머신이 구동하지 않았고 해당 주문이 추가 되지 않았다면 세번째 timer_machine을 구동합니다.*/
					}else if(!ORDER_SEND.hasStarted3&&!in_order_number){
						
						/* arraylist 에 "해당 주문 번호"(ordtake.seq) 추가 한다. */
						ORDER_SEND.check_order_number.add(seq);
						
						/* 세번째  머신이 실행되었다고 선언한다. */
						ORDER_SEND.hasStarted3 = true;
						
						/* 세번째  머신을 구동한다. */
						tm.timer_machine3(seq,Tradeid,mobilid,prdtprice);
					
					/* 네번째 머신이 구동하지 않았고 해당 주문이 추가 되지 않았다면 네번째 timer_machine을 구동합니다.*/
					}else if(!ORDER_SEND.hasStarted4&&!in_order_number){
						
						/* arraylist 에 "해당 주문 번호"(ordtake.seq) 추가 한다. */
						ORDER_SEND.check_order_number.add(seq);
						
						/* 네번째  머신이 실행되었다고 선언한다. */
						ORDER_SEND.hasStarted4 = true;
						
						/* 네번째  머신을 구동한다. */
						tm.timer_machine4(seq,Tradeid,mobilid,prdtprice);
					}
					
					
					// 없는 값 System.out.println(dao.rs().getString("bo_no"));
					if(contains(VALUES, pay_status))
					{
						update_delivery_cancel(seq);
					}

					/* 배달중인 것을 배달 완료로 변경 3시간 후 */
					if(pay_status.equals("di"))
					{

						update_delivery_complete();
					}
				} /* while(dao.rs().next()) {...} */
			    
			} catch (SQLException e) {
				Utils.getLogger().warning(e.getMessage());
				DBConn.latest_warning = "ErrPOS031";
				e.printStackTrace();
			} catch (Exception e) {
				Utils.getLogger().warning(e.getMessage());
				Utils.getLogger().warning(Utils.stack(e));
				DBConn.latest_warning = "ErrPOS032";
			}finally {
				dao.closePstmt();
			}
		}
	}

	

	
	/**
	 * set_
	 * fcm을 전송한다.
	 * 
	 */	
	public static boolean set_notification(String seq,String order_try_count) 
	{
		// TODO Auto-generated method stub
		/* 1. GCM을 전송한다. */
		
		
		/* 2. 변수에 성공 실패 여부를 반환한다. */
		/* 공통부분 */
		/*
		출처: http://javastudy.tistory.com/80 [믿지마요 후회해요]
		*/
		Boolean is_gcm=false;
		String query="";
		URL targetURL;
		URLConnection urlConn = null;
	      
		
		try {
			Map<String,Object> params = new LinkedHashMap<>(); // 파라미터 세팅
	        params.put("seq", seq);
	        params.put("order_try_count", order_try_count);
	        
	        
			StringBuilder postData = new StringBuilder();
	        for(Map.Entry<String,Object> param : params.entrySet()) {
	            if(postData.length() != 0) postData.append('&');
	            postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
	            postData.append('=');
	            postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
	        }

	        byte[] postDataBytes = postData.toString().getBytes("UTF-8");
	        // https://img.cashq.co.kr/api/token/set_notification.php?seq=1858&order_try_count=1
			targetURL = new URL("https://img.cashq.co.kr/api/token/set_notification.php");
			urlConn = targetURL.openConnection();
			HttpURLConnection cons = (HttpURLConnection) urlConn;
			// 헤더값을 설정한다.
			cons.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			cons.setRequestMethod("POST");
	        cons.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
	        
			
			//cons.getOutputStream().write("LOGIN".getBytes("UTF-8"));
			cons.setDoOutput(true);
			cons.setDoInput(true);
			cons.setUseCaches(false);
			cons.setDefaultUseCaches(false);
	        cons.getOutputStream().write(postDataBytes); // POST 호출


	     //   출처: https://nine01223.tistory.com/256 [스프링연구소(spring-lab)]
			/*
			PrintWriter out = new PrintWriter(cons.getOutputStream());
			out.close();*/
			//System.out.println(query);
			/* parameter setting */
			OutputStream opstrm=cons.getOutputStream();
			opstrm.write(query.getBytes());
			opstrm.flush();
			opstrm.close();

			String buffer = null;
			String bufferHtml="";
			BufferedReader in = new BufferedReader(new InputStreamReader(cons.getInputStream()));

			 while ((buffer = in.readLine()) != null) {
				 bufferHtml += buffer;
			}
			 //System.out.println(bufferHtml);
			 JSONObject object = (JSONObject)JSONValue.parse(bufferHtml);
			 //String success=object.get("success").toString();
			/* 
			int success_count=Integer.parseInt(success);
			 if(success_count>0){
				 is_gcm=true;
			 }
			 */
			//Utils.getLogger().info(bufferHtml);
			in.close();
			
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			Utils.getLogger().warning(e.getMessage());
			Utils.getLogger().warning(Utils.stack(e));
			DBConn.latest_warning = "ErrPOS035";
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			Utils.getLogger().warning(e.getMessage());
			Utils.getLogger().warning(Utils.stack(e));
			DBConn.latest_warning = "ErrPOS036";
		}catch(NullPointerException e){
			
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println("error registration_ids null");
		}
		return is_gcm;
	}


	/* 들어 있는 배열의 존재 여부 존재 하면 true, 존재하지 않으면 false 를 출력한다. */
	public static <T> boolean contains(final T[] array, final T v) {
	    for (final T e : array)
	        if (e == v || v != null && v.equals(e))
	            return true;

	    return false;
	}

	/*********************************************
	 * update_delivery_cancel
	 * 주문 후 5분이 지난 배달대기 배달 중 주문은 배달취소(dd:denied_delivery) 로 변경 한다.
	 * @param string seq  
	 *********************************************/
	public static void update_delivery_cancel(String seq) {

		MyDataObject dao = new MyDataObject();
		StringBuilder sb = new StringBuilder();
		
		try {
				sb.append("update cashq.ordtake SET pay_status='ad',exam_num1='4',up_time=now() ");
				sb.append(" where  seq=? ;");
				dao.openPstmt(sb.toString());
				
				dao.pstmt().setString(1, seq);
				dao.pstmt().executeUpdate();
				
				dao.tryClose();
		} catch (SQLException e) {
			Utils.getLogger().warning(e.getMessage());
			DBConn.latest_warning = "ErrPOS037";
			e.printStackTrace();
		}
		catch (Exception e) {
			Utils.getLogger().warning(e.getMessage());
			DBConn.latest_warning = "ErrPOS038";
			Utils.getLogger().warning(Utils.stack(e));
		}
		finally {
			dao.closePstmt();
		}
	}

	/**
	 * update_delivery_complete
	 * 주문 건을 자동으로 3시간이 지난 건은 배달 완료 라고 보고 배달 완료로 변경한다.
	 * exam_num1 = 3 
	 * pay_statusc = 'dc' (delivery Complete)
	 */
	public static void update_delivery_complete() {

		MyDataObject dao = new MyDataObject();
		StringBuilder sb = new StringBuilder();
		
		try {
				sb.append("update cashq.ordtake SET pay_status='dc',exam_num1='3' ");
				sb.append(" where date_add(up_time,interval 3 hour)<now() ");
				sb.append(" and pay_status in ('di') ;");
				dao.openPstmt(sb.toString());

				dao.pstmt().executeUpdate();
				
				dao.tryClose();
		} catch (SQLException e) {
			Utils.getLogger().warning(e.getMessage());
			DBConn.latest_warning = "ErrPOS037";
			e.printStackTrace();
		}
		catch (Exception e) {
			Utils.getLogger().warning(e.getMessage());
			DBConn.latest_warning = "ErrPOS038";
			Utils.getLogger().warning(Utils.stack(e));
		}
		finally {
			dao.closePstmt();
		}
	}

	/* 랜덤 6자리를 불러 옵니다. */
	public static int get_rand_int() 
	{
	    String numStr = "1";
	    String plusNumStr = "1";
	    for (int i = 0; i < 6; i++) {
	        numStr += "0";
	        if (i != 6 - 1) {
	            plusNumStr += "0";
	        }
	    }
	 
	    Random random = new Random();
	    int result = random.nextInt(Integer.parseInt(numStr)) + Integer.parseInt(plusNumStr);
	 
	    if (result > Integer.parseInt(numStr)) {
	        result = result - Integer.parseInt(plusNumStr);
	    }
	    return result;
	}

	/**
	 * 해당 상점을 사용 가능으로 변경한다.
	 * @param safen0504
	 * @param safen_in010
	 * @param mapping_option
	 * @param retCode
	 */
	private static void update_ata(String callid) {

		MyDataObject dao = new MyDataObject();
		StringBuilder sb = new StringBuilder();
		
		try {
				sb.append("update ifpl.cdr set ATA_SEND=? where CALLID=? limit 1");
				dao.openPstmt(sb.toString());
				dao.pstmt().setString(1, Utils.get_now());
				dao.pstmt().setString(2, callid);

				dao.pstmt().executeUpdate();
				
				dao.tryClose();
		} catch (SQLException e) {
			Utils.getLogger().warning(e.getMessage());
			DBConn.latest_warning = "ErrPOS037";
			e.printStackTrace();
		}
		catch (Exception e) {
			Utils.getLogger().warning(e.getMessage());
			DBConn.latest_warning = "ErrPOS038";
			Utils.getLogger().warning(Utils.stack(e));
		}
		finally {
			dao.closePstmt();
		}
	}



	/**
	 * get_store_info
	 * 가맹점 정보를 불러온다. 
	 * @param seq(seq)
	 * @return store <Stirng,String>
	 */
	private static Map<String, String> get_store_info(String seq) {
		// TODO Auto-generated method stub
		Map<String, String> store=new HashMap<String, String>();

		
		StringBuilder sb = new StringBuilder();
		MyDataObject dao = new MyDataObject();
		
		sb.append("SELECT * FROM cashq.store where seq=? limit 1");
		try {
			dao.openPstmt(sb.toString());
			dao.pstmt().setString(1, seq);
			//System.out.println(seq);
			dao.setRs (dao.pstmt().executeQuery());

			while(dao.rs().next()) 
			{
				store = getResultMapRows(dao.rs());
			}			
		}catch (SQLException e) {
			Utils.getLogger().warning(e.getMessage());
			DBConn.latest_warning = "ErrPOS039";
			e.printStackTrace();
		}catch (Exception e) {
			Utils.getLogger().warning(e.getMessage());
			Utils.getLogger().warning(Utils.stack(e));
			DBConn.latest_warning = "ErrPOS040";
		}
		finally {
			dao.closePstmt();
		}
		return store;
	}

	/**
	 * @param bt_content
	 * 메세지 전문과 변환될 텍스트가 지정 되어 있습니다. 정해진 룰의 패턴이 지정 되어 있습니다.
	 *  패턴의 예 
	 *  #{매장명}을 이용해 주셔서 #{050번호}
	 * 
	 * @param bt_regex
	 *  룰의 규칙을 넣습니다. bt_content에서 선언한 #{키값}의 모든 패턴은 아래와 같이 모두 선언 되어 있어야 합니다.        
	 *  
	 *  예) 
	 *  #{매장명}=store.name&#{050번호}=store.tel
	 *  
	 *  라면 두개의 규칙이 존재하고 #{매장명}을 store.name의 맵의 키로 지정합니다.  
	 * @param messageMap
	 *  위에서 지정한 store.name의 키가 함수 호출전에 아래와 같은 형태로 정의 되어 인수로 들어가야 합니다.
	 *  Map<String, String> messageMap=new HashMap<String, String>();
		messageMap.put("store.name","태부치킨");
	 * @return
	 */
	private static String chg_regexrule(String bt_content, String bt_regex, Map<String, String> messageMap) {
		// TODO Auto-generated method stub
		String returnValue="";
		try{
			if(bt_regex.indexOf("&")>-1)
			{
				String[] regex_array=bt_regex.split("&");
				String[] keys;
				/* bt_regex 의 크기 만큼 반복하여 변환한다. */
				for (int i = 0; i < regex_array.length; i++) {
					keys=regex_array[i].split("=");
					bt_content=bt_content.replace(keys[0], messageMap.get(keys[1]));
				}
				returnValue=bt_content;
			}else{
				returnValue=bt_content;
			}
		}catch(NullPointerException e){
			returnValue=bt_content;
		}
		return returnValue;
	}


	/**
	 * 사이트 푸시로그를 전송합니다.  
	 * 입력 : 푸시 인포.앱아이디, stype,biz_code, caller, called, wr_subject, wr_content result
	 */
	private static void set_site_push_log(Map<String, String> push_info) {
		// TODO Auto-generated method stub
		StringBuilder sb = new StringBuilder();
		MyDataObject dao = new MyDataObject();

		  
		sb.append("insert into `bc_alrim_log` set ");
		sb.append("al_appid='bdc',");
		sb.append("al_hp=?,");
		sb.append("al_sender=?,");
		sb.append("bp_code=?,");
		sb.append("bs_code=?,");
		sb.append("al_type=?,");
		sb.append("al_subject=?,");
		sb.append("al_content=?,");
		sb.append("al_datetime=now(),");
		sb.append("al_result=?,");
		sb.append("Tradeid=?;");
		;
		try {
			dao.openPstmt(sb.toString());
			dao.pstmt().setString(1, push_info.get("al_hp"));
			dao.pstmt().setString(2, push_info.get("al_sender"));
			dao.pstmt().setString(3, push_info.get("bp_code"));
			dao.pstmt().setString(4, push_info.get("bs_code"));
			dao.pstmt().setString(5, push_info.get("al_type"));
			dao.pstmt().setString(6, push_info.get("al_subject"));
			dao.pstmt().setString(7, push_info.get("al_content"));
			dao.pstmt().setString(8, push_info.get("al_result"));
			dao.pstmt().setString(9, push_info.get("Tradeid"));
			
			dao.pstmt().executeUpdate();
		} catch (SQLException e) {
			Utils.getLogger().warning(e.getMessage());
			Utils.getLogger().warning(Utils.stack(e));
			DBConn.latest_warning = "ErrPOS060";
		} catch (Exception e) {
			Utils.getLogger().warning(e.getMessage());
			Utils.getLogger().warning(Utils.stack(e));
			DBConn.latest_warning = "ErrPOS061";
		} finally {
			dao.closePstmt();
		}
	}


	/**
	 * 비즈톡에 알림톡(카카오톡 비즈니스 메세지를 전송합니다.)를 전송합니다.  
	 * 입력 : 푸시 인포.앱아이디, stype,biz_code, caller, called, wr_subject, wr_content result
	 */
	private static int set_em_mmt_tran(Map<String, String> ata_info) {
		// TODO Auto-generated method stub
		StringBuilder sb = new StringBuilder();
		StringBuilder sb2 = new StringBuilder();
		MyDataObject dao = new MyDataObject();
		MyDataObject dao2 = new MyDataObject();
		int wr_idx=0;
		sb.append("INSERT INTO biztalk.em_mmt_tran SET ");
		sb.append("date_client_req=SYSDATE(), ");
		sb.append("template_code=?,");
		sb.append("content=?,");
		sb.append("recipient_num=?,");
		sb.append("callback=?,");
		sb.append("msg_status='1',");
		sb.append("subject=' ', ");
		sb.append("sender_key=?, ");
		sb.append("service_type='3', ");
		sb.append("msg_type='1008';");
		try {

			dao.openPstmt(sb.toString());
			dao.pstmt().setString(1, ata_info.get("template_code"));
			dao.pstmt().setString(2, ata_info.get("content"));
			dao.pstmt().setString(3, ata_info.get("mb_hp"));
			dao.pstmt().setString(4, ata_info.get("tel"));
			dao.pstmt().setString(5, ata_info.get("sender_key"));
			dao.pstmt().executeUpdate();
			
			sb2.append("select LAST_INSERT_ID() last_id;");
			dao2.openPstmt(sb2.toString());
			dao2.setRs(dao2.pstmt().executeQuery());
			
			if (dao2.rs().next()) {
				wr_idx= dao2.rs().getInt("last_id");
			}
		} catch (SQLException e) {
			Utils.getLogger().warning(e.getMessage());
			Utils.getLogger().warning(Utils.stack(e));
			DBConn.latest_warning = "ErrPOS060";
		} catch (Exception e) {
			Utils.getLogger().warning(e.getMessage());
			Utils.getLogger().warning(Utils.stack(e));
			DBConn.latest_warning = "ErrPOS061";
		} finally {
			dao.closePstmt();
			dao2.closePstmt();
		}
		return wr_idx;
		
	}

	/**
	 * 해당 상점을 사용 가능으로 변경한다.
	 * @param safen0504
	 * @param safen_in010
	 * @param mapping_option
	 * @param retCode
	 */
	private static void update_status() {

		MyDataObject dao = new MyDataObject();
		StringBuilder sb = new StringBuilder();
		String hist_table = "SMSQ_SEND_" + Utils.getYYYYMM();		
		String seqno= "";		
		String status_code= "";		
		try {
				sb.append("select seqno,status_code from ifpl.");
				sb.append(hist_table);
				sb.append(" where seqno in (select al_result from bdcook.bc_alrim_log where al_type='ATASEND' and al_result<1000);");
				
				
				dao.openPstmt(sb.toString());
				dao.setRs(dao.pstmt().executeQuery());
			  /* 2. 값이 있으면 */
			   while(dao.rs().next()) {
				
				   seqno=dao.rs().getString("seqno");
				   status_code=dao.rs().getString("status_code");
				   update_alrim_log(seqno,status_code);
			   }
				dao.tryClose();
		} catch (SQLException e) {
			Utils.getLogger().warning(e.getMessage());
			DBConn.latest_warning = "ErrPOS037";
			e.printStackTrace();
		}
		catch (Exception e) {
			Utils.getLogger().warning(e.getMessage());
			DBConn.latest_warning = "ErrPOS038";
			Utils.getLogger().warning(Utils.stack(e));
		}
		finally {
			dao.closePstmt();
		}
	}

	/**
	 * 해당 상점을 사용 가능으로 변경한다.
	 * @param safen0504
	 * @param safen_in010
	 * @param mapping_option
	 * @param retCode
	 */
	private static void update_alrim_log(String seqno,String status_code) {

		MyDataObject dao = new MyDataObject();
		StringBuilder sb = new StringBuilder();
		
		try {
				sb.append("update bdcook.bc_alrim_log set al_result=? where al_result=?");
				dao.openPstmt(sb.toString());
				dao.pstmt().setString(1, status_code);
				dao.pstmt().setString(2, seqno);

				dao.pstmt().executeUpdate();
				
				dao.tryClose();
		} catch (SQLException e) {
			Utils.getLogger().warning(e.getMessage());
			DBConn.latest_warning = "ErrPOS037";
			e.printStackTrace();
		}
		catch (Exception e) {
			Utils.getLogger().warning(e.getMessage());
			DBConn.latest_warning = "ErrPOS038";
			Utils.getLogger().warning(Utils.stack(e));
		}
		finally {
			dao.closePstmt();
		}
	}

	public static boolean isCellphone(String str) {
	
	    //010, 011, 016, 017, 018, 019
	
	    return Pattern.matches("01(?:0|1|[6-9])(?:\\d{3}|\\d{4})\\d{4}", str);
	
	}

	/**
     * ResultSet을 Row마다 Map에 저장후 List에 다시 저장.
     * @param rs DB에서 가져온 ResultSet
     * @return Listt<map> 형태로 리턴
     * @throws Exception Collection
     */
    private static Map<String, String> getResultMapRows(ResultSet rs) throws Exception
    {
        // ResultSet 의 MetaData를 가져온다.
        ResultSet metaData = (ResultSet) rs;
        // ResultSet 의 Column의 갯수를 가져온다.
        
        int sizeOfColumn = metaData.getMetaData().getColumnCount();
        
        Map<String, String> list = new HashMap<String, String>();
        
        String column_name;
        
        // rs의 내용을 돌려준다.
        if(sizeOfColumn>0)
        {
            // Column의 갯수만큼 회전
            for (int indexOfcolumn = 0; indexOfcolumn < sizeOfColumn; indexOfcolumn++)
            {
                column_name = metaData.getMetaData().getColumnName(indexOfcolumn + 1);
                // map에 값을 입력 map.put(columnName, columnName으로 getString)
                list.put(column_name,rs.getString(column_name));

            }
        }
        return list;
    }
    // 출처: https://moonleafs.tistory.com/52 [달빛에 스러지는 낙엽들.]
    
    

	/**
	 * check_order
	 * 주문 정보인 seq로 주문을 받았는지 받지 않았는지 체크 한다. 받지 않았으면 false를 받았으면 true를 리턴한다.  
	 * @param String seq 주문 번호
	 */
	public static boolean check_order(String seq) {
		// TODO Auto-generated method stub
		StringBuilder sb = new StringBuilder();
		MyDataObject dao = new MyDataObject();
		boolean did_you_order = false;
		
		sb.append("select * from cashq.ordtake where seq=? ");

	
		try {
			dao.openPstmt(sb.toString());
			dao.pstmt().setString(1, seq);
			dao.setRs (dao.pstmt().executeQuery());
			if (dao.rs().next()) {
				/*0 exam*/
				did_you_order = !"0".equals(dao.rs().getString("exam_num1"));
			}

		} catch (SQLException e) {
			Utils.getLogger().warning(e.getMessage());
			Utils.getLogger().warning(Utils.stack(e));
			DBConn.latest_warning = "ErrPOS060";
		} catch (Exception e) {
			Utils.getLogger().warning(e.getMessage());
			Utils.getLogger().warning(Utils.stack(e));
			DBConn.latest_warning = "ErrPOS061";
		} finally {
			dao.closePstmt();
		}
		return did_you_order;
		
	}

	/**
	 * check_order
	 * 주문 정보인 seq로 주문을 받았는지 받지 않았는지 체크 한다. 받지 않았으면 false를 받았으면 true를 리턴한다.  
	 * @param String seq 주문 번호
	 */
	public static String check_order_result(String seq) {
		// TODO Auto-generated method stub
		StringBuilder sb = new StringBuilder();
		MyDataObject dao = new MyDataObject();
		String did_you_order = "0";
		
		sb.append("select * from cashq.ordtake where seq=? ");

	
		try {
			dao.openPstmt(sb.toString());
			dao.pstmt().setString(1, seq);
			dao.setRs (dao.pstmt().executeQuery());
			if (dao.rs().next()) {
				/* 
				 * exam_num1
				 * "0"  = 입력 초기 값
				 * "1"  = 주문 승인
				 * "2"  = 주문 취소
				 * 
				 * */
				did_you_order = dao.rs().getString("exam_num1");
			}

		} catch (SQLException e) {
			Utils.getLogger().warning(e.getMessage());
			Utils.getLogger().warning(Utils.stack(e));
			DBConn.latest_warning = "ErrPOS060";
		} catch (Exception e) {
			Utils.getLogger().warning(e.getMessage());
			Utils.getLogger().warning(Utils.stack(e));
			DBConn.latest_warning = "ErrPOS061";
		} finally {
			dao.closePstmt();
		}
		return did_you_order;
		
	}
	/*
	 * http://cashq.co.kr/adm/ext/kgmobilians/card/cancel/cn_cancel_req.php?seq=2037&form_mode=auto_cancel
	 * 자동 취소 부분 신 모듈 
	 * */
	public static boolean set_kgorder_autocancel_from_url(String tradeid, String mobilid,String prdtprice) 
	{
		// TODO Auto-generated method stub
		Boolean is_gcm=false;
		String query="";
		URL targetURL;
		URLConnection urlConn = null;
	      
		
		try {
			Map<String,Object> params = new LinkedHashMap<>(); // 파라미터 세팅
			params.put("mode","CN07");
			params.put("recordKey","cashq.co.kr");
			params.put("svcId","190517071943");
			params.put("partCancelYn","N");
			params.put("tradeId",tradeid);
			params.put("mobilId",mobilid);
			params.put("prdtPrice",prdtprice);
	        
			StringBuilder postData = new StringBuilder();
	        for(Map.Entry<String,Object> param : params.entrySet()) {
	            if(postData.length() != 0) postData.append('&');
	            postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
	            postData.append('=');
	            postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
	        }

	        byte[] postDataBytes = postData.toString().getBytes("UTF-8");
	        
			targetURL = new URL("http://cashq.co.kr/adm/ext/kgmobilians/card/cancel/cn_cancel_result.php");
			urlConn = targetURL.openConnection();
			HttpURLConnection cons = (HttpURLConnection) urlConn;
			// 헤더값을 설정한다.
			cons.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");	
			cons.setRequestMethod("POST");
	        cons.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
	        
			
			//cons.getOutputStream().write("LOGIN".getBytes("UTF-8"));
			cons.setDoOutput(true);
			cons.setDoInput(true);
			cons.setUseCaches(false);
			cons.setDefaultUseCaches(false);
	        cons.getOutputStream().write(postDataBytes); // POST 호출


	     //   출처: https://nine01223.tistory.com/256 [스프링연구소(spring-lab)]
			/*
			PrintWriter out = new PrintWriter(cons.getOutputStream());
			out.close();*/
			//System.out.println(query);
			/* parameter setting */
			OutputStream opstrm=cons.getOutputStream();
			opstrm.write(query.getBytes());
			opstrm.flush();
			opstrm.close();

			String buffer = null;
			String bufferHtml="";
			BufferedReader in = new BufferedReader(new InputStreamReader(cons.getInputStream()));

			 while ((buffer = in.readLine()) != null) {
				 bufferHtml += buffer;
			}
			 System.out.println(bufferHtml);
			 //System.out.println(bufferHtml);
			 JSONObject object = (JSONObject)JSONValue.parse(bufferHtml);
			 //String success=object.get("success").toString();
			/* 
			int success_count=Integer.parseInt(success);
			 if(success_count>0){
				 is_gcm=true;
			 }
			 */
			//Utils.getLogger().info(bufferHtml);
			in.close();
			
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			Utils.getLogger().warning(e.getMessage());
			Utils.getLogger().warning(Utils.stack(e));
			DBConn.latest_warning = "ErrPOS035";
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			Utils.getLogger().warning(e.getMessage());
			Utils.getLogger().warning(Utils.stack(e));
			DBConn.latest_warning = "ErrPOS036";
		}catch(NullPointerException e){
			
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println("error registration_ids null");
		}
		return is_gcm;
	}



	/**
	 * get_mobilid
	 * 모바일 아이디를 불러온다. 
	 * @param Tradeid
	 * @return mobilid
	 */
	private static String get_mobilid(String tradeid) {
		// TODO Auto-generated method stub
		String mobilid = "";

		
		StringBuilder sb = new StringBuilder();
		MyDataObject dao = new MyDataObject();
		
		sb.append("select Mobilid from order_anp where Tradeid=?");
		try {
			dao.openPstmt(sb.toString());
			dao.pstmt().setString(1, tradeid);
			//System.out.println(seq);
			dao.setRs (dao.pstmt().executeQuery());

			while(dao.rs().next()) 
			{
				mobilid = dao.rs().getString("Mobilid");
			}			
		}catch (SQLException e) {
			Utils.getLogger().warning(e.getMessage());
			DBConn.latest_warning = "ErrPOS039";
			e.printStackTrace();
		}catch (Exception e) {
			Utils.getLogger().warning(e.getMessage());
			Utils.getLogger().warning(Utils.stack(e));
			DBConn.latest_warning = "ErrPOS040";
		}
		finally {
			dao.closePstmt();
		}
		return mobilid;
	}
	

	/*
	 * http://cashq.co.kr/adm/ext/kgmobilians/mobile/cancel/cancel_result.php?tradeid=2037&form_mode=auto_cancel
	 * 자동 취소 부분 모바일 구 모듈 
	 * */
	public static boolean set_kgmobile_order_autocancel_from_url(String tradeid, String mobilid,String prdtprice) 
	{
		// TODO Auto-generated method stub
		Boolean is_gcm=false;
		String query="";
		URL targetURL;
		URLConnection urlConn = null;
	      
		
		try {
			Map<String,Object> params = new LinkedHashMap<>(); // 파라미터 세팅
			params.put("Mrchid1","16100602");
			params.put("Svcid1","161006029244");
			params.put("Tradeid1",tradeid);
			params.put("Prdtprice1",prdtprice);
			params.put("Mobilid1",mobilid);
			params.put("can_cnt","1");

			StringBuilder postData = new StringBuilder();
	        for(Map.Entry<String,Object> param : params.entrySet()) {
	            if(postData.length() != 0) postData.append('&');
	            postData.append(URLEncoder.encode(param.getKey(), "UTF-8"));
	            postData.append('=');
	            postData.append(URLEncoder.encode(String.valueOf(param.getValue()), "UTF-8"));
	        }

	        byte[] postDataBytes = postData.toString().getBytes("UTF-8");
	        
			targetURL = new URL("http://cashq.co.kr/adm/ext/kgmobilians/mobile/cancel/cancel_result.php");
			urlConn = targetURL.openConnection();
			HttpURLConnection cons = (HttpURLConnection) urlConn;
			// 헤더값을 설정한다.
			cons.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");	
			cons.setRequestMethod("POST");
	        cons.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
	        
			
			//cons.getOutputStream().write("LOGIN".getBytes("UTF-8"));
			cons.setDoOutput(true);
			cons.setDoInput(true);
			cons.setUseCaches(false);
			cons.setDefaultUseCaches(false);
	        cons.getOutputStream().write(postDataBytes); // POST 호출


	     //   출처: https://nine01223.tistory.com/256 [스프링연구소(spring-lab)]
			/*
			PrintWriter out = new PrintWriter(cons.getOutputStream());
			out.close();*/
			//System.out.println(query);
			/* parameter setting */
			OutputStream opstrm=cons.getOutputStream();
			opstrm.write(query.getBytes());
			opstrm.flush();
			opstrm.close();

			String buffer = null;
			String bufferHtml="";
			BufferedReader in = new BufferedReader(new InputStreamReader(cons.getInputStream()));

			 while ((buffer = in.readLine()) != null) {
				 bufferHtml += buffer;
			}
			 System.out.println(bufferHtml);
			 //System.out.println(bufferHtml);
			 JSONObject object = (JSONObject)JSONValue.parse(bufferHtml);
			 //String success=object.get("success").toString();
			/* 
			int success_count=Integer.parseInt(success);
			 if(success_count>0){
				 is_gcm=true;
			 }
			 */
			//Utils.getLogger().info(bufferHtml);
			in.close();
			
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			Utils.getLogger().warning(e.getMessage());
			Utils.getLogger().warning(Utils.stack(e));
			DBConn.latest_warning = "ErrPOS035";
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			Utils.getLogger().warning(e.getMessage());
			Utils.getLogger().warning(Utils.stack(e));
			DBConn.latest_warning = "ErrPOS036";
		}catch(NullPointerException e){
			
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println("error registration_ids null");
		}
		return is_gcm;
	}
	
	
	/**
	 * send_arlimtalk(is_order_done1, seq);
	 * @param e
	 */
	public static void send_arlimtalk(String exam_num1, String seq)
	{
		Map<String, String> ordtake_info = new HashMap<String, String>();
		
		/* 알림톡 템플릿 키값이 들어 갈 key value 배열 객체 */
		Map<String, String> messageMap=new HashMap<String, String>();

		Map<String, String> ata_info = new HashMap<String, String>();
		
		Map<String, String> message_info = new HashMap<String, String>();
		
		/* 플러스친구 */
		Map<String, String> plusfriend=new HashMap<String, String>();
		
		String appid = "";
		String messages = "";
		String mb_hp = "";
		
		/* 비즈톡에 입력된 값 */
		int wr_idx=0;
		
		/* cashq.ordtake 정보를 seq로 불러 온다. */
		ordtake_info = getOrdtake(seq);
		
		mb_hp = ordtake_info.get("mb_hp");
		
		/* 주문정보 (ordtake)로 TempleteInfo를 messageMap에 담는다. */
		messageMap = getTempleteInfo(ordtake_info);
		
		appid = messageMap.get("appid");
		
		message_info = get_bt_template(exam_num1,appid,"call_point");
		
		plusfriend=getSenderKey(appid);
		
		messages=chg_regexrule(message_info.get("ata_message"),message_info.get("ata_regex"), messageMap);
		
		System.out.println(messages);
		
		ata_info.put("template_code",message_info.get("bt_code"));
		ata_info.put("content",messages);
		ata_info.put("mb_hp",mb_hp);
		ata_info.put("tel",ordtake_info.get("st_vphone"));
		ata_info.put("sender_key",plusfriend.get("bp_senderid"));

		/* 알림톡 주문 승인 메시지를 전송합니다. */
		wr_idx = set_em_mmt_tran(ata_info);

		
	}



	
	/**
	 * @param appid
	 * @return
	 */
	private static Map<String, String> getSenderKey(String appid) {
		// TODO Auto-generated method stub
		
			// TODO Auto-generated method stub
		Map<String, String> plusfriend=new HashMap<String, String>();
		
		StringBuilder sb = new StringBuilder();
		MyDataObject dao = new MyDataObject();
		sb.append("SELECT * FROM cashq.bt_plusfriend where bp_appid = ?");
		try {
			dao.openPstmt(sb.toString());
			dao.pstmt().setString(1, appid);
			dao.setRs (dao.pstmt().executeQuery());
			while(dao.rs().next()) 
			{
				plusfriend.put("bp_senderid", dao.rs().getString("bp_senderid"));
				plusfriend.put("bp_status", dao.rs().getString("bp_status"));
				plusfriend.put("bp_terminate_date", dao.rs().getString("bp_terminate_date"));
				plusfriend.put("bp_stop_date", dao.rs().getString("bp_stop_date"));
			}			
		}catch (SQLException e) {
			Utils.getLogger().warning(e.getMessage());
			DBConn.latest_warning = "ErrPOS039";
			e.printStackTrace();
		}catch (Exception e) {
			Utils.getLogger().warning(e.getMessage());
			Utils.getLogger().warning(Utils.stack(e));
			DBConn.latest_warning = "ErrPOS040";
		}
		finally {
			dao.closePstmt();
		}
		return plusfriend;
	}
	

	
	/**
	 * @param appid
	 * @return
	 */
	private static Map<String, String> getOrdtake(String seq) {
		// TODO Auto-generated method stub
		
			// TODO Auto-generated method stub
		Map<String, String> ordtake_info =new HashMap<String, String>();
		
		StringBuilder sb = new StringBuilder();
		MyDataObject dao = new MyDataObject();
		sb.append("SELECT * FROM cashq.ordtake where seq = ?");
		try {
			dao.openPstmt(sb.toString());
			dao.pstmt().setString(1, seq);
			dao.setRs (dao.pstmt().executeQuery());
			while(dao.rs().next()) 
			{
				ordtake_info = getResultMapRows(dao.rs());
			}			
		}catch (SQLException e) {
			Utils.getLogger().warning(e.getMessage());
			DBConn.latest_warning = "ErrPOS039";
			e.printStackTrace();
		}catch (Exception e) {
			Utils.getLogger().warning(e.getMessage());
			Utils.getLogger().warning(Utils.stack(e));
			DBConn.latest_warning = "ErrPOS040";
		}
		finally {
			dao.closePstmt();
		}
		return ordtake_info;
	}
	
	private static Map<String, String> getTempleteInfo(Map<String, String> ordtake) {
		// TODO Auto-generated method stub
		
		// 템플릿 정보
		Map<String, String> templete_info =new HashMap<String, String>();
		
		// 주문 정보
		Map<String, String> ordtake_info =new HashMap<String, String>();
		
		// 주문 정보
		Map<String, String> agency_info =new HashMap<String, String>();
		
		// 시아이디 포인트 정보
		Map<String, String> cid_point_info =new HashMap<String, String>();
		
		// 상점 정보
		Map<String, String> store =new HashMap<String, String>();
		
		// 메시지 정보
		Map<String, String> message_info =new HashMap<String, String>();
		
		String mb_hp = "";
		String st_no = "";
		String biz_code = "";
		String appid = "";

		ordtake_info = getOrdtake(ordtake.get("seq"));
		
		mb_hp = ordtake_info.get("mb_hp");
		
		st_no = ordtake_info.get("st_seq");
		
		store = get_store_info(st_no);
		
		biz_code = store.get("biz_code");
		
		agency_info = get_agency(biz_code);
		
		/* #{업체명} 	store.name */
		templete_info.put("store.name",ordtake_info.get("st_name"));
		
		/* 매장(상점)전화번호 */
		templete_info.put("store.tel",ordtake_info.get("st_vphone"));

		appid = agency_info.get("appid");

		templete_info.put("appid",appid);
		
		/* 템플릿 메세지를 가져옵니다. */
		message_info=get_bt_template(ordtake_info.get("exam_num1"),appid,"cid_point");
		
		/* 적립금액(미션조건), #{적립금액}  */
		templete_info.put("agencyMember.point_items",getPointSet(agency_info.get("point_items")));
		
		/* 포인트 최소 인정금액 */
		templete_info.put("agencyMember.min_point",String.format("%d", Integer.parseInt(agency_info.get("min_point"))));
		
		/* 대리점 관리자의 핸드폰 번호를 불러 옵니다. 기본값 01077430009 */
		templete_info.put("agencyMember.cell",agency_info.get("cell"));
		
		/* 랜덤 6자리 치환 */
		templete_info.put("function.get_rand_int",String.valueOf(get_rand_int()));

		templete_info.put("downlink","http://hdu.cashq.co.kr/m/p/");
		
		 /* 개인이 보유한 모든 사용가능 0507_point에 발급한 모든  포인트 합산 금액을 불러옵니다. */
		templete_info.put("function.total_point",get_total_point(mb_hp));
		
		/* 개인이 보유한 모든 사용가능 0507_point.status=1인 0507_point.point포인트가 sum한 결과를 불러옵니다. */
		templete_info.put("function.get_point",get_point(mb_hp));
		
		/* agencyMember.pointset=cashbag
		 * #{사용가능포인트} = cashq.agencyMember.minimum_point 
		 * */
		templete_info.put("agencyMember.minimum_point",agency_info.get("minimum_point"));
		
		/* 상점 공유 링크를 가져 옵니다. 
		 * http://bdmt.cashq.co.kr/m/p/?seq=%s 형태로 상점 링크를 생성합니다.
		 * */
		templete_info.put("store.sharelink",get_sharelink(st_no));
		
		/* 로그에 기록된 시아이디포인트를 가져온다. */
		templete_info.put("prq_store.cid_point",cid_point_info.get("prq_store.cid_point"));

		int exam_num1 = Integer.parseInt(ordtake_info.get("exam_num1"));
		
		/* #{취소사유}	function.get_exam_num1 */
		templete_info.put("function.get_order_status",get_order_status(exam_num1));
		
		/* #{주문일시}	ordtake.insdate */
		templete_info.put("ordtake.insdate",ordtake_info.get("insdate"));
		
		/* #{주문번호}	ordtake.Tradeid */
		templete_info.put("ordtake.Tradeid",ordtake_info.get("Tradeid"));
		
		/* #{배달주소}	ordtake.mb_address */
		templete_info.put("ordtake.mb_address",ordtake_info.get("mb_addr1")+" "+ordtake_info.get("mb_addr2"));
		
		/* #{메뉴명}	 ordtake.ord_name */
		templete_info.put("ordtake.ord_name",ordtake_info.get("ord_name"));
		
		/* #{대리점명}	agencyMember.agency_name */
		templete_info.put("agencyMember.agency_name",agency_info.get("agency_name"));
		
		/* #{주문확인링크}	ordtake.confirmation_link */
		templete_info.put("ordtake.confirmation_link","http://bdtalk.co.kr/m/p/");
		

		int exam_num2 = Integer.parseInt(ordtake_info.get("exam_num2"));
		
		/* #{배달시간}  function.get_delivery_time */
		templete_info.put("function.get_delivery_time",get_delivery_time(exam_num2));
		
		return templete_info;
	}

	

	/**
	 * @param point_set ="3_5000&5_10000&10_20000"
	 * @return
	 * 3개 5,000원
	 * 5개 10,000원
	 * 10개 20,000원
	 */
	private static String getPointSet(String point_set) {
		// TODO Auto-generated method stub
		String[] regex_array=point_set.split("&");
		String[] keys;
		String returnValue="";
		try{
			/* bt_regex 의 크기 만큼 반복하여 변환한다. */
			for (int i = 0; i < regex_array.length; i++) {
				keys=regex_array[i].split("_");
				returnValue=returnValue+keys[0]+"회 주문시 "+String.format("%,d", Integer.parseInt(keys[1]))+"원\n";
			}
		}catch(ArrayIndexOutOfBoundsException e){
			returnValue="";
			returnValue=returnValue+"5회 주문시 10,000원\n";
			returnValue=returnValue+"10회 주문시  20,000원\n";
		}
		return returnValue;
	}



	
	/**
	 * @param appid
	 * @return
	 */
	private static String get_total_point(String mb_hp) {
		// TODO Auto-generated method stub
		
			// TODO Auto-generated method stub
		String total_point = "0";
		
		StringBuilder sb = new StringBuilder();
		MyDataObject dao = new MyDataObject();
		sb.append("SELECT sum(point) total_point FROM cashq.0507_point where mb_hp = ?  and ev_ed_dt>now() and status='1' ");
		
		try {
			dao.openPstmt(sb.toString());
			dao.pstmt().setString(1, mb_hp);
			dao.setRs (dao.pstmt().executeQuery());
			while(dao.rs().next()) 
			{
				total_point = dao.rs().getString("total_point");
			}			
		}catch (SQLException e) {
			Utils.getLogger().warning(e.getMessage());
			DBConn.latest_warning = "ErrPOS039";
			e.printStackTrace();
		}catch (Exception e) {
			Utils.getLogger().warning(e.getMessage());
			Utils.getLogger().warning(Utils.stack(e));
			DBConn.latest_warning = "ErrPOS040";
		}
		finally {
			dao.closePstmt();
		}
		return total_point;
	}

	/* 사용자가 가진 모든 포인트 값을 환산하여 더한 연산 결과를 가져옵니다.
	 * 
	 */
	private static String get_point(String mb_hp){
		String point = "0";
		StringBuilder sb = new StringBuilder();
		MyDataObject dao = new MyDataObject();
		sb.append("SELECT ");
		sb.append(" sum(point) sum_point ");
		sb.append("FROM cashq.0507_point ");
		sb.append("where mb_hp = ? ");
		sb.append(" and status='1';");
		
		try {
			dao.openPstmt(sb.toString());
			dao.pstmt().setString(1, mb_hp);
			dao.setRs (dao.pstmt().executeQuery());
			while(dao.rs().next()) 
			{
				point = dao.rs().getString("sum_point");
			}			
		}catch (SQLException e) {
			Utils.getLogger().warning(e.getMessage());
			DBConn.latest_warning = "ErrPOS039";
			e.printStackTrace();
		}catch (Exception e) {
			Utils.getLogger().warning(e.getMessage());
			Utils.getLogger().warning(Utils.stack(e));
			DBConn.latest_warning = "ErrPOS040";
		}
		finally {
			dao.closePstmt();
		}		
		return point;
	}



	/**
	 * 대리점 정보를 biz_code로 불러 옵니다. 
	 * 불러온 정보는 hashMap  에 적재하여 리턴합니다.
	 * @param string
	 * @return HashMap
	 */
	private static Map<String, String> get_agency(String biz_code) {
		// TODO Auto-generated method stub
		Map<String, String> agency=new HashMap<String, String>();
		agency.put("appid","cashq");
		agency.put("agency_name","대리점명");
		agency.put("pointset","off");
		agency.put("point_items","5_10000&10_20000");
		agency.put("min_point","12000");
		agency.put("minimum_point","10000");
		agency.put("cell","01077430009");
		
		
		StringBuilder sb = new StringBuilder();
		MyDataObject dao = new MyDataObject();
		sb.append("select * from cashq.agencyMember where biz_code = ?");
		try {
			dao.openPstmt(sb.toString());
			dao.pstmt().setString(1, biz_code);
			dao.setRs (dao.pstmt().executeQuery());

			if (dao.rs().next()) {
				agency.put("appid",dao.rs().getString("appid"));
				agency.put("agency_name",dao.rs().getString("agency_name"));
				if(dao.rs().getString("pointset").equals("on"))
				{
					agency.put("pointset","on");
					agency.put("point_items",dao.rs().getString("point_items"));
				}
				agency.put("min_point",dao.rs().getString("min_point"));
				agency.put("cell",dao.rs().getString("cell"));
				agency.put("minimum_point",dao.rs().getString("minimum_point"));
			}			
		}catch (SQLException e) {
			Utils.getLogger().warning(e.getMessage());
			DBConn.latest_warning = "ErrPOS039";
			e.printStackTrace();
		}catch (Exception e) {
			Utils.getLogger().warning(e.getMessage());
			Utils.getLogger().warning(Utils.stack(e));
			DBConn.latest_warning = "ErrPOS040";
		}
		finally {
			dao.closePstmt();
		}

		return agency;
	}


	/* 가맹점이 설정한 코드 공유 링크 정보를 가져옵니다.
	 * 
	 */
	private static String get_sharelink(String st_no){
	String sharelink = "";
	sharelink = String.format("http://bdmt.cashq.co.kr/m/p/?seq=%s",st_no);

	return sharelink;
	}

	/* 
	 * get_order_status
	 * exam_num1에 따른 주문 상태를 불러 옵니다.
	 * @param int exam_num1
	 * @return String
	 */
	private static String get_order_status(int exam_num1)
	{
		String order_status = "";
		String[] order_array = {"신규주문","주문접수","주문취소","배달완료","자동취소","승인 후 취소"};
		
		order_status=order_array[exam_num1];
	
		return order_status;
	}
	

	/* 
	 * get_delivery_time
	 * exam_num1에 따른 주문 상태를 불러 옵니다.
	 * @param int exam_num1
	 * @return String
	 */
	private static String get_delivery_time(int exam_num2)
	{
		String order_status = "";
		
		String[] order_array = {"30분","40분","50분","60분","70분","80분","90분"};
		
		order_status=order_array[exam_num2];
	
		return order_status;
	}



	/**
	 * get_bt_template
	 * @param appid
	 * @param ed_type
	 * @return Map<String, String>
	 */
	private static Map<String, String> get_bt_template(String exam_num1, String appid,String ed_type) {
		// TODO Auto-generated method stub
		Map<String, String> message=new HashMap<String, String>();

		
		StringBuilder sb = new StringBuilder();
		MyDataObject dao = new MyDataObject();
		sb.append("select * from cashq.bt_template ");
		sb.append(" where exam_num1=? ");
		//sb.append("  and bt_status='access' ");
		sb.append("  and appid=? ");
		sb.append("  and ed_type=? ");
		
		
		
		try {
			dao.openPstmt(sb.toString());
			dao.pstmt().setString(1, exam_num1);
			dao.pstmt().setString(2, appid);
			dao.pstmt().setString(3, ed_type);
			dao.setRs (dao.pstmt().executeQuery());

			while(dao.rs().next()) 
			{
				message.put("bt_type",dao.rs().getString("bt_type"));
				System.out.println(dao.rs().getString("bt_type"));
				if(dao.rs().getString("bt_type").equals("gcm"))
				{
					message.put("gcm_title",dao.rs().getString("bt_name"));
					message.put("gcm_message",dao.rs().getString("bt_content"));
					message.put("gcm_regex",dao.rs().getString("bt_regex"));
					message.put("gcm_status",dao.rs().getString("bt_status"));
				}
				else if(dao.rs().getString("bt_type").equals("ata"))
				{
					message.put("ata_title",dao.rs().getString("bt_name"));					
					message.put("ata_message",dao.rs().getString("bt_content"));
					message.put("ata_regex",dao.rs().getString("bt_regex"));
					message.put("bt_code",dao.rs().getString("bt_code"));
					message.put("ata_status",dao.rs().getString("bt_status"));
				}
				else if(dao.rs().getString("bt_type").equals("sms"))
				{
					message.put("sms_title",dao.rs().getString("bt_name"));
					message.put("sms_message",dao.rs().getString("bt_content"));
					message.put("sms_regex",dao.rs().getString("bt_regex"));
					message.put("sms_status",dao.rs().getString("bt_status"));
				}
			}			
		}catch (SQLException e) {
			Utils.getLogger().warning(e.getMessage());
			DBConn.latest_warning = "ErrPOS039";
			e.printStackTrace();
		}catch (Exception e) {
			Utils.getLogger().warning(e.getMessage());
			Utils.getLogger().warning(Utils.stack(e));
			DBConn.latest_warning = "ErrPOS040";
		}
		finally {
			dao.closePstmt();
		}
		return message;
	}



}
