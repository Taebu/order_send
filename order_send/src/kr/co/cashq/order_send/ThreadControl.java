/**
 * 
 */
package kr.co.cashq.order_send;

/**
 * @author Taebu
 *
 */
public class ThreadControl {

	public static String st_no = "0";
	
	public String getSt_no() {
		return st_no;
	}

	public void setSt_no(String st_no) {
		this.st_no = st_no;
	}

	static void print(String message) {
		String threadName = Thread.currentThread().getName();
		System.out.format("%s: %s%n", threadName, message);
	}

	private static class MessageLoop implements Runnable {
		private static boolean is_order = false;
		private static boolean is_auto_cancel = false;

		@Override
		public void run() {
			try {
				for (int i = 0; i < 30; i++) {
					print(st_no + "주문을 시도 합니다. " + i + " 번째 아직 주문이 아직 승인되지 않았습니다 !!! ");
					Thread.sleep(10 * 1000);
				}
			} catch (InterruptedException e) {
				print("아직 끝나지 않았어요");
			}
		}
	}

	public static void main(String[] args) throws InterruptedException {
		int tries = 0;

		print("추가적인 스레드를 시작합니다.");
		Thread t = new Thread(new MessageLoop());
		t.start();
		MessageLoop ml = new MessageLoop();
		// print(ml.is_order?"t":"f");
		print("추가적인 스레드가 끝나기를 기다립니다.");
		String is_order = "0"; 
		ThreadControl tc =new ThreadControl(); 
		
		
		while (t.isAlive()) {
			// print ("아직 기다립니다.");
			if(!tc.equals("0"))
			{
				is_order = Order_fcm_queue.check_order_result(tc.getSt_no());
				
				if (is_order.equals("1")) {
					// print ("주문이 승인 되었습니다.");
					ml.is_order = true;
					t.interrupt();
				}
				
				
				if (is_order.equals("2")) {
					ml.is_order = false;
					t.interrupt();
				}
				System.out.println("tries : " + tries);
				if (tries > 30) {
					print("주문을 안 받다니 도저히 참을 수 없네요! 주문을 취소 합니다.");
					ml.is_auto_cancel = true;
					t.interrupt();
					t.join();
				}
				tries++;
				t.sleep(10 * 1000);
			}
		}

		print("메인 스레드 종료!");
		if(!ml.is_auto_cancel){
		print(ml.is_order ? "주문이 승인 되었습니다. " : "주문이 거절 되었습니다. ");
		}else if (ml.is_auto_cancel) {
			print("자동 취소 되었습니다. !!!");
		}
		
		// TODO Auto-generated method stub
	}

}