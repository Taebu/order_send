# order_send
order_send를 .
root@175.126.82.182:/home/hosting_users/cashq/www/adm/0507_point_cash_status.php
경로의 프로그램이 crontab으로 구동 되는 것을 java project로 변경
kr.co.cashq.safen_cdr 을 복사하여 프로젝트 진행.
2018-01-06 진행
방식의 자바 프로그래밍 방식으로 변경 합니다.

## Java 수정 후 구동 되게 만들기
- 해당 경로 이동
> $cd /home/point/safen_point

- 구동 확인 숫자가 나오면 멈추고 빌드 해야 한다.
> $sh ./status.sh

- 멈추는 명령 
> $sh ./stop.sh

- java to class
> $sh ./mask.sh

- safen_cdr 재구동
> $sh ./startw.sh

- safen_cdr 재구동 확인
> $sh ./status.sh