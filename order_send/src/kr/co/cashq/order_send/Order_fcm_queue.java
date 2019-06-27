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
						tm.timer_machine1(seq);
					
					/* 두번째 머신이 구동하지 않았고 해당 주문이 추가 되지 않았다면 두번째 timer_machine을 구동합니다.*/
					}else if(!ORDER_SEND.hasStarted2&&!in_order_number){
						
						/* arraylist 에 "해당 주문 번호"(ordtake.seq) 추가 한다. */
						ORDER_SEND.check_order_number.add(seq);
						
						/* 두번째  머신이 실행되었다고 선언한다. */
						ORDER_SEND.hasStarted2 = true;
						
						/*두번째  머신을 구동한다. */
						tm.timer_machine2(seq);

					/* 세번째 머신이 구동하지 않았고 해당 주문이 추가 되지 않았다면 세번째 timer_machine을 구동합니다.*/
					}else if(!ORDER_SEND.hasStarted3&&!in_order_number){
						
						/* arraylist 에 "해당 주문 번호"(ordtake.seq) 추가 한다. */
						ORDER_SEND.check_order_number.add(seq);
						
						/* 세번째  머신이 실행되었다고 선언한다. */
						ORDER_SEND.hasStarted3 = true;
						
						/* 세번째  머신을 구동한다. */
						tm.timer_machine3(seq);
					
					/* 네번째 머신이 구동하지 않았고 해당 주문이 추가 되지 않았다면 네번째 timer_machine을 구동합니다.*/
					}else if(!ORDER_SEND.hasStarted4&&!in_order_number){
						
						/* arraylist 에 "해당 주문 번호"(ordtake.seq) 추가 한다. */
						ORDER_SEND.check_order_number.add(seq);
						
						/* 네번째  머신이 실행되었다고 선언한다. */
						ORDER_SEND.hasStarted4 = true;
						
						/* 네번째  머신을 구동한다. */
						tm.timer_machine4(seq);
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
	        https://img.cashq.co.kr/api/token/set_notification.php?seq=1858&order_try_count=1
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
		sb.append("insert into ifpl.SMSQ_SEND SET "); 
		sb.append("dest_no=?, ");
		sb.append("call_back=?,");
		sb.append("msg_contents=?,");
		sb.append("msg_instm=now(),");
		sb.append("sendreq_time=now(),");
		sb.append("Msg_Type='K',");
		sb.append("title_Str='테스트3', ");
		sb.append("k_template_code=?, ");
		sb.append("k_next_type='N'; ");
		
		try {

			dao.openPstmt(sb.toString());
			dao.pstmt().setString(1, ata_info.get("dest_no"));
			dao.pstmt().setString(2, ata_info.get("call_back")); //0236675279
			dao.pstmt().setString(3, ata_info.get("msg_contents"));
			dao.pstmt().setString(4, ata_info.get("k_template_code"));
			
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
}
