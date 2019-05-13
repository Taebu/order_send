package kr.co.cashq.order_send;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
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
 *  @param['url']="http://baedalcook.co.kr/ 
 *   목적 : https://github.com/nike3265/baedalcook/issues/84
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
		String bo_status="";
		String Tradeid="";
		String bo_no="";
		String bs_code="";
		String mb_address="";
		
		final String[] VALUES = new String[] {"pay_complete","pay_real_card","pay_real_cash"};
		/* 메세지 정보 */
		Map<String, String> message_info = new HashMap<String, String>();

		/* 푸시 정보 */
		Map<String, String> push_info = new HashMap<String, String>();
		String messages="";
		String[] regex_rule;
		String[] regex_array;
		int eventcnt = 0;
		
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
		
		/* 비즈톡에 입력된 값 */
		int wr_idx=0;	
		if (con != null) {
			MyDataObject dao = new MyDataObject();

			StringBuilder sb = new StringBuilder();
			//StringBuilder sb_log = new StringBuilder();
			/* 
			 * 1. 아래 조건을 만족하는 `0507_point` 테이블을 조회한다.  
			 * 	조건 1) 오늘 입력된것.
			 * 조건 2) 상태가 0인것.
			 * 조건 3) "비즈코드"와 "이벤트코드"가 일치하는 것.
			 * distinct는 할필요 없었다.
			 */
					
			sb.append("select * from ordtake where 1=1 ");
			
			/* 결제완료를  조회 한다. */
			sb.append(" and  pay_status in ('pc')");
			
			/* 결제가 이루어진 시점을 기준으로 5분이 지난 건을 조회 한다. */
			sb.append(" and date_add(insdate,interval 5 minute)<now() ");
			
			//sb.append(" and mb_hp='01077430009' ");
			sb.append(" ;");
			/*
			 *  
  		     * */
			try {
				dao.openPstmt(sb.toString());
				dao.setRs(dao.pstmt().executeQuery());
			  /* 2. 값이 있으면 */
			   while(dao.rs().next()) {
					ORDER_SEND.heart_beat = 1;
					bs_code=dao.rs().getString("MSTR");
					mb_hp=dao.rs().getString("mb_hp");
					Tradeid=dao.rs().getString("Tradeid");
					
					
					/* 배달승인 템플릿 */
					message_info=get_bt_template("SJT_018117");

					mb_hp=mb_hp.replaceAll("\\-", "/").replaceAll("\\+82", "0").trim();
					
					is_hp=isCellphone(mb_hp);
					
					/* 배달주문의 고유 번호(bo_no)를 불러 옵니다. */
					bo_no=dao.rs().getString("bo_no");
					bo_status=dao.rs().getString("bo_status");
					
					System.out.println(dao.rs().getString("bo_no"));
					if(contains(VALUES, bo_status ))
					{
						update_delivery_cancel(bo_no);
					/* 3. 배달 주문을 캔슬 상태인 `delivery_cancel` 로 변경합니다. */
					
					
						
					
						String urls="http://baedalcook.co.kr/v2/ajax/set_fcm_cancel/"+Tradeid+"/"+mb_hp;
					
						//
						set_fcm(urls);
						if(message_info.get("ata_status").equals("access")&&is_hp)
						{
							/* 배달취소 템플릿 */
							message_info=get_bt_template("SJT_018118");								

							mb_address=dao.rs().getString("mb_addr1")+" "+dao.rs().getString("mb_addr2");
							Map<String, String> messageMap=new HashMap<String, String>();
							/* #{업체명} */
							messageMap.put("bc_order.bo_insdate",dao.rs().getString("bo_insdate"));

							/* #{주문번호} */
							messageMap.put("bc_order.Tradeid",dao.rs().getString("Tradeid"));

							/* #{050번호} */
							messageMap.put("bc_order.bs_name",dao.rs().getString("bs_name"));

							/* #{배달주소} */
							messageMap.put("bc_order.mb_addr",mb_address);

							/* #{메뉴명} */
							messageMap.put("bc_order.Prdtnm",dao.rs().getString("Prdtnm"));

							/* #{취소일시} */
							messageMap.put("bc_order.bo_update",Utils.get_now());
							
							messages=chg_regexrule(message_info.get("ata_message"),message_info.get("ata_regex"), messageMap);
							System.out.println(messages);
							

							/* gcm 전송 실패시  */
							regex_rule=message_info.get("ata_regex").split("&");
							messages=chg_regexrule(message_info.get("ata_message"),message_info.get("ata_regex"), messageMap);

						

								/* ATA 전송*/
								Map<String, String> ata_info = new HashMap<String, String>();
								ata_info.put("dest_no",mb_hp);
								ata_info.put("call_back","0236675279");
								ata_info.put("msg_contents",messages);
								ata_info.put("k_template_code","SJT_018118");
								wr_idx=set_em_mmt_tran(ata_info);

								/* Site_push_log*/

								push_info.put("al_hp",mb_hp);
								push_info.put("al_sender","0236675279");
								

								push_info.put("al_type","ATASEND");
								push_info.put("al_subject",message_info.get("ata_title"));
								push_info.put("al_content",messages);
								push_info.put("al_result",String.valueOf(wr_idx));
								push_info.put("Tradeid",dao.rs().getString("Tradeid"));
								
								
								/* 전송 성공 여부에 따라 사이트 푸시 로그를 생성합니다.*/
								set_site_push_log(push_info);
						
						}

					}
					
					if(bo_status.equals("delivery_wait")||bo_status.equals("delivery_handling"))
					{
						/* 배달중인 것을 배달 완료로 변경 3시간 후 */
						update_delivery_complete();
					}
				} /* while(dao.rs().next()) {...} */
			   
			   doMainProcess2();
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
	 * safen_cmd_queue 테이블의 데이터를 처리하기 위한 주요한 처리를 수행한다.
	 * 
	 */
	public static void doMainProcess2() {
		Connection con = DBConn.getConnection();
		/* 대리점 정보 */
		Map<String, String> agency_info   = new HashMap<String, String>();
		/* 상점 정보 */
		Map<String, String> store_info      = new HashMap<String, String>();
		
		/* 메세지 정보 */
		Map<String, String> message_info = new HashMap<String, String>();
		
		Map<String, String> push_info = new HashMap<String, String>();
		/* 플러스친구 */
		Map<String, String> plusfriend=new HashMap<String, String>();
		
		String biz_code="";
		
		String mb_hp="";
		String appid="cashq";
		
		String st_no="";
		
		String messages="";
		String[] regex_rule;
		String[] regex_array;
		int eventcnt = 0;
		
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
		
		/* 비즈톡에 입력된 값 */
		int wr_idx=0;
		String sender_key="";
		String result_message="전달성공";
		String virtual_number="";


		if (con != null) {
			MyDataObject dao = new MyDataObject();
			MyDataObject dao2 = new MyDataObject();
			MyDataObject dao3 = new MyDataObject();
			MyDataObject dao4 = new MyDataObject();
			MyDataObject dao5 = new MyDataObject();

			StringBuilder sb = new StringBuilder();
			//StringBuilder sb_log = new StringBuilder();
			/* 
			 * 1. 아래 조건을 만족하는 `0507_point` 테이블을 조회한다.  
			 * 	조건 1) 오늘 입력된것.
			 * 조건 2) 상태가 0인것.
			 * 조건 3) "비즈코드"와 "이벤트코드"가 일치하는 것.
			 * distinct는 할필요 없었다.
			 */
			sb.append("select ");
			sb.append(" *,");
			sb.append("FROM_BASE64(SEND_PHONE) SEND_PHONE2,");
			sb.append("FROM_BASE64(RECEIVE_PHONE) RECEIVE_PHONE2 ");
			sb.append(" from ifpl.`cdr` ");
			
			/* 대기중 인 결제완료, 현장카드, 현장현금 건을 조회 한다. */
			sb.append(" where 1=1 and DURATION>50");
			sb.append("  and ATA_SEND is NULL ");		
			sb.append(" ; ");
			
			try {
				dao.openPstmt(sb.toString());
				dao.setRs(dao.pstmt().executeQuery());
			  /* 2. 값이 있으면 */
			   while(dao.rs().next()) {
				   ORDER_SEND.heart_beat = 1;
					
					mb_hp=dao.rs().getString("SEND_PHONE2");
					is_hp=isCellphone(mb_hp.trim());
					
					
					/* 3. 포인트를 사용가능으로 변경합니다. */
					update_ata(dao.rs().getString("CALLID"));
					
					virtual_number=dao.rs().getString("VIRTUALNUMBER");
					
					/* 4. 포인트 갯수를 센다.  */	

					
					/* 12. 상점 정보를 불러 옵니다. */
					store_info=get_store(virtual_number);
					
					/* 템플릿 메세지를 가져옵니다. */
					message_info=get_bt_template("SJT_018119");
					

     				//	System.out.println(message_info.get("ata_message"));
					
					
					/**/
					if(message_info.get("ata_status").equals("access")&&is_hp){
						/*
						 * #{거래일시}=bc_order.bo_insdate
#{주문번호}=bc_order.Tradeid
#{업체명}=bc_order.bs_name
#{메뉴명}=bc_order.Prdtnm
#{배달주소}=bc_order.mb_addr
#{취소일시}=bc_order.bo_update	
						 * */
						
						Map<String, String> messageMap=new HashMap<String, String>();
						/* #{업체명} */
						messageMap.put("bc_store.bs_name",store_info.get("bs_name"));

						/* #{050번호} */
						messageMap.put("bc_store.bs_vphone",virtual_number);
						
						/* #{주문일시} */
						messageMap.put("cdr.CONN_TIME",dao.rs().getString("CONN_TIME"));
						
						/* #{고객센터} */
						messageMap.put("service_center","02-3667-5279");
								
						/* 템플릿을 정해진 패턴대로 변경 합니다. 
						 * @param bt_content 템플릿 내용, 
						 * @param bt_regex 템플릿 패턴
						 * @param messageMap 템플릿 패턴을 바꿀 내용
						 * */
						/* gcm messages */
						messages=chg_regexrule(message_info.get("ata_message"),message_info.get("ata_regex"), messageMap);
						System.out.println(messages);
						

						/* gcm 전송 실패시  */
						regex_rule=message_info.get("ata_regex").split("&");
						messages=chg_regexrule(message_info.get("ata_message"),message_info.get("ata_regex"), messageMap);

					

							/* ATA 전송*/
							Map<String, String> ata_info = new HashMap<String, String>();
							ata_info.put("dest_no",mb_hp);
							ata_info.put("call_back","0236675279");
							ata_info.put("msg_contents",messages);
							ata_info.put("k_template_code","SJT_018119");
							wr_idx=set_em_mmt_tran(ata_info);

							/* Site_push_log*/

							push_info.put("al_hp",mb_hp);
							push_info.put("al_sender","0236675279");
							
							push_info.put("bp_code",store_info.get("bp_code"));
							push_info.put("bs_code",store_info.get("bs_code"));
							push_info.put("al_type","ATASEND");
							push_info.put("al_subject",message_info.get("ata_title"));
							push_info.put("al_content",messages);
							push_info.put("al_result",String.valueOf(wr_idx));
							
							
							/* 전송 성공 여부에 따라 사이트 푸시 로그를 생성합니다.*/
							set_site_push_log(push_info);
							update_status();
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
				dao2.closePstmt();
				dao3.closePstmt();
				dao4.closePstmt();
				dao5.closePstmt();
			}
			
			//콜로그 마스터 정보의 레코드 1개를 갱신을 시도한다.
			//Safen_master.doWark2();
		}
	}

	
	/**
	 * safen_cmd_queue 테이블의 데이터를 처리하기 위한 주요한 처리를 수행한다.
	 * 
	 */
	public static void doMainProcess21() {
		Connection con = DBConn.getConnection();
		String mb_hp="";
		String bo_status="";
		String Tradeid="";
		String bo_no="";
		String bs_code="";
		final String[] VALUES = new String[] {"pay_complete","pay_real_card","pay_real_cash"};
		if (con != null) {
			MyDataObject dao = new MyDataObject();

			StringBuilder sb = new StringBuilder();
			//StringBuilder sb_log = new StringBuilder();
			/* 
			 * 1. 아래 조건을 만족하는 `0507_point` 테이블을 조회한다.  
			 * 	조건 1) 오늘 입력된것.
			 * 조건 2) 상태가 0인것.
			 * 조건 3) "비즈코드"와 "이벤트코드"가 일치하는 것.
			 * distinct는 할필요 없었다.
			 */
					
			sb.append("select ");
			sb.append(" *,");
			sb.append("FROM_BASE64(SEND_PHONE) SEND_PHONE2,");
			sb.append("FROM_BASE64(RECEIVE_PHONE) RECEIVE_PHONE2 ");
			sb.append(" from ifpl.`cdr` ");
			
			/* 대기중 인 결제완료, 현장카드, 현장현금 건을 조회 한다. */
			sb.append(" where 1=1 and DURATION>50");
			sb.append("  and ATA_SEND is NULL ");		
			sb.append(" ; ");
			/*
			 *  
  		     * */
			try {
				dao.openPstmt(sb.toString());
				dao.setRs(dao.pstmt().executeQuery());
			  /* 2. 값이 있으면 */
			   while(dao.rs().next()) {
					ORDER_SEND.heart_beat = 1;
					bs_code=dao.rs().getString("SEND_PHONE2");
					mb_hp=dao.rs().getString("RECEIVE_PHONE2");
					
					
					if(contains(VALUES, bo_status ))
					{
						update_delivery_cancel(bo_no);
					/* 3. 배달 주문을 캔슬 상태인 `delivery_cancel` 로 변경합니다. */
					
					
					mb_hp=mb_hp.replaceAll("\\-", "/");
					
					String urls="http://baedalcook.co.kr/v2/ajax/set_fcm_cancel/"+Tradeid+"/"+mb_hp;
					
					//
					set_fcm(urls);
					}
					
					if(bo_status.equals("delivery_wait")||bo_status.equals("delivery_handling"))
					{
						/* 배달중인 것을 배달 완료로 변경 3시간 후 */
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
	
	private static boolean set_fcm(String urls) 
	{
		// TODO Auto-generated method stub
		/* 1. GCM을 전송한다. */
		
		/* 2. 변수에 성공 실패 여부를 반환한다. */
		/* 공통부분 */
		/*
		URL url = new URL("JSON 주소");
		InputStreamReader isr = new InputStreamReader(url.openConnection().getInputStream(), "UTF-8");
		JSONObject object = (JSONObject)JSONValue.parse(isr);

		출처: http://javastudy.tistory.com/80 [믿지마요 후회해요]
		*/
		Boolean is_gcm=false;
		String query="";
		URL targetURL;
		URLConnection urlConn = null;
	      
		
		try {
			//opinion = new URL(homepage, "opinion/deitorial.htm"); // 상대 경로로 생성
			
			//targetURL = new URL("http://baedalcook.co.kr/v2/ajax/set_fcm_cancel/"+Tradeid+"/"+mb_hp);
			targetURL = new URL(urls);
			urlConn = targetURL.openConnection();
			HttpURLConnection cons = (HttpURLConnection) urlConn;
			// 헤더값을 설정한다.
			cons.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			cons.setRequestMethod("POST");
			
			//cons.getOutputStream().write("LOGIN".getBytes("UTF-8"));
			cons.setDoOutput(true);
			cons.setDoInput(true);
			cons.setUseCaches(false);
			cons.setDefaultUseCaches(false);
			
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
	 * 3시간이 지난 배달대기 배달 중 주문은 배달 완료로 변경 한다. 
	 *********************************************/
	private static void update_delivery_cancel(String bo_no) {

		MyDataObject dao = new MyDataObject();
		StringBuilder sb = new StringBuilder();
		
		try {
				sb.append("update bdcook.bc_order SET bo_status='delivery_cancel' ");
				sb.append(" where  bo_no=? ;");
				dao.openPstmt(sb.toString());
				
				dao.pstmt().setString(1, bo_no);
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
	 * 해당 상점을 사용 가능으로 변경한다.
	 * @param safen0504
	 * @param safen_in010
	 * @param mapping_option
	 * @param retCode
	 */
	private static void update_delivery_complete() {

		MyDataObject dao = new MyDataObject();
		StringBuilder sb = new StringBuilder();
		
		try {
				sb.append("update bdcook.bc_order SET bo_status='delivery_complete' ");
				sb.append(" where date_add(bo_insdate,interval 3 hour)<now() ");
				sb.append(" and bo_status in ('delivery_wait','delivery_handling') ;");
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
	 * @param string
	 * @return
	 */
	private static Map<String, String> get_store(String virtual_number) {
		// TODO Auto-generated method stub
		Map<String, String> store_info=new HashMap<String, String>();
		store_info.put("bs_name","매장명");
		store_info.put("bs_vphone","05012341234");
		store_info.put("bs_phone","0212341234");
		store_info.put("bs_code","B0000001");
		store_info.put("bp_code","admin");
		
		StringBuilder sb = new StringBuilder();
		MyDataObject dao = new MyDataObject();
		sb.append("SELECT * FROM bdcook.bc_store where bs_vphone=? limit 1");
		try {
			dao.openPstmt(sb.toString());
			dao.pstmt().setString(1, virtual_number);
			System.out.println(virtual_number);
			dao.setRs (dao.pstmt().executeQuery());

			if (dao.rs().next()) {
				store_info.put("bs_name",dao.rs().getString("bs_name"));
				store_info.put("bs_vphone",dao.rs().getString("bs_vphone"));
				store_info.put("bs_phone",dao.rs().getString("bs_phone"));
				store_info.put("bs_code",dao.rs().getString("bs_code"));
				store_info.put("bp_code",dao.rs().getString("bp_code"));				
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
		return store_info;
	}


	/**
	 * @param appid
	 * @return
	 */
	private static Map<String, String> get_bt_template(String bt_code) {
		// TODO Auto-generated method stub
		Map<String, String> message=new HashMap<String, String>();

		
		StringBuilder sb = new StringBuilder();
		MyDataObject dao = new MyDataObject();
		
		sb.append("SELECT * FROM bdcook.bt_template where bt_code=? limit 1");
		try {
			dao.openPstmt(sb.toString());
			dao.pstmt().setString(1, bt_code);
			System.out.println(bt_code);
			dao.setRs (dao.pstmt().executeQuery());

			while(dao.rs().next()) 
			{
			
			
				
				message.put("ata_title",dao.rs().getString("bt_name"));					
				message.put("ata_message",dao.rs().getString("bt_content"));
				message.put("ata_regex",dao.rs().getString("bt_regex"));
				message.put("bt_code",dao.rs().getString("bt_code"));
				message.put("ata_status",dao.rs().getString("bt_status"));
				
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
}
