/**
 * 
 */
package kr.co.cashq.order_send;

/**
 * @author Taebu
 *
 */
 
 import java.util.Timer;
 import java.util.TimerTask;

 /**
  * @author Taebu
  * timer 4 개를 선언하여 4개의 주문을 동시에 처리 할수 있도록, Timer 와 TimerTask 를 선언하고 구동 중여부를 판단 할 수 있는 변수를 선언 한다.
  * 
  * 그리고 구동 중이면 구동중이기에 구동 중인 Timer에게는 일을 시키지 않는다.
  * 
  *  TimerMachine
  */
 public class TimerMachine {

 	public static int count1,count2,count3,count4;
 	
 	public static Timer m_timer1,m_timer2,m_timer3,m_timer4;
 	public static TimerTask m_task1,m_task2,m_task3,m_task4;
 	public static boolean hasStarted1,hasStarted2,hasStarted3,hasStarted4;

 	/* 생성자 초기화 변수 */
 	TimerMachine(){
 		hasStarted1 = false;
 		hasStarted2 = false;
 		hasStarted3 = false;
 		hasStarted4 = false;
 	}
 	
 	public static boolean isHasStarted2() {
		return hasStarted2;
	}

	public static void setHasStarted2(boolean hasStarted2) {
		TimerMachine.hasStarted2 = hasStarted2;
	}

	boolean getHasStarted1()
 	{
 		return hasStarted1;
 	}

 	void setHasStarted1(boolean hasStarted1)
 	{
 		TimerMachine.hasStarted1 = hasStarted1;
 	}
 	
 	/* timer machine 1 호기 */
 	static void timer_machine1(String seq){
 		
 		if(hasStarted1){
 			System.out.println("천번째 자동 실행중 ");
 		}else{
 			System.out.println("천번째 자동 ");
 		}
 		m_timer1 = new Timer();
 		m_task1 = new TimerTask(){
 			
 			/* 주문 초기 진입시 무조건 초기 값은 0 값입니다. 승인시 "1"로 선언하고, 거절시 "2"로 선언 됩니다. */
 			String order_result = "0";
 			
 			boolean is_order1 = false;
 			boolean is_auto_cancel1 = false;
 			
 			public void run() {
 				hasStarted1 = true;
 			    
 				order_result = Order_fcm_queue.check_order_result(seq);
 				
 				/* 주문 승인*/
 				if(order_result.equals("1")){
 					/* 타이머 1 을 중지 합니다. */
 					m_timer1.cancel();
 					
 					/* 머신을 중지 합니다. */
 					hasStarted1 = false;
 					
 					/* 주문이 승인되었다고 선언 */
 					is_order1 = true;
 					set_order("승인");
 					count1 = 0;
 				}
 				
 				/* 주문 거절 */
 				if(order_result.equals("2")){
 					/* 타이머 1 을 중지 합니다. */
 					m_timer1.cancel();
 					
 					/* 머신을 중지 합니다. */
 					hasStarted1 = false;
 					
 					/* 주문이 거절 되었다고 선언 */
 					is_order1 = false;
 					set_order("거절");
 					count1 = 0;
 				}
 				
 				if(count1<30){
 					System.out.println("첫번째 머신이 주문을 시도 합니다. " + count1 + " 번째 아직 주문이 아직 승인되지 않았습니다 !!! ");
 					count1++;
 				}
				
				if (count1 >= 30) {
					
					System.out.println("주문을 안 받다니 도저히 참을 수 없네요! 주문을 취소 합니다.");
					
					/* 타이머 1 을 중지 합니다. */
 					m_timer1.cancel();
 					
					/* 자동 취소 되었습니다.  라고 선언*/
					is_auto_cancel1 = true;
					
					set_auto_cancel();
					count1 = 0;
				} 				
 				
 			}
 		};
 		m_timer1.schedule(m_task1, 0,10000);
 	}
 	
 	
 	/* 주문 승인에 대한 메서드 */
 	/* 주문 거절에 대한 메서드 */
 	static void set_order(String order_stauts){
 		System.out.println("주문 "+order_stauts+" 메서드 실행");
 		
 	}
 	
 	
 	/* 자동 최소에 대한 메서드 */
 	static void set_auto_cancel(){
 		System.out.println("자동 취소 메서드 실행");
 	}
 	

 }


