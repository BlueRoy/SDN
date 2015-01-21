import httplib2
import json
import time
import MySQLdb

#globals
baseUrl = 'http://192.168.56.1:8080/controller/nb/v2'
containerName = 'default/'

#get the data from opendaylight
def get_node_stats():
    url = baseUrl + '/statistics/default/port/'
    resp, content = h.request(url, "GET")
    node_stats = json.loads(content)  
    node_info = node_stats['portStatistics']
    for stats in node_info:
        port_info = stats['portStatistic']
        for info in port_info:
            print_node_port_stats(info)
            submit_data(info)
#print                      
def print_node_port_stats(stats):
    print '{0:25} {1:6} {2:12} {3:12} {4:12} {5:12}'.format('NODE', 'PORT', 'TXPKTCNT', 'TXBYTES', 'RXPKTCNT', 'RXBYTES',)
    print '{0:25} {1:6} {2:5} {3:12} {4:12} {5:12}'.format(stats['nodeConnector']['node']['id'],stats['nodeConnector']['id'], stats['transmitPackets'], stats['transmitBytes'], stats['receivePackets'], stats['receiveBytes'])
    print '----------------------------------------------------------------------------------'

#put data into MySQL table       
def submit_data(stats):
    nodeid = stats['nodeConnector']['node']['id']
    portid = stats['nodeConnector']['id']
    txp = str(stats['transmitPackets'])
    txb = str(stats['transmitBytes'])
    rxp = str(stats['receivePackets'])
    rxb = str(stats['receiveBytes'])
    sys_time = time.strftime('%Y-%m-%d %H:%M:%S',time.localtime(time.time()))
    t = (nodeid,portid,txp,txb,rxp,rxb,sys_time)
    conn = MySQLdb.connect(host='127.0.0.1',user='root',db ='test')
    cur = conn.cursor()
    #cur.execute("create table openflow(NODE varchar(30),PORT varchar(5),TXPKTCNT varchar(10),TXBYTES varchar(10), RXPKTCNT varchar(10), RXBYTES varchar(10), TIME varchar(20))")
    sqli = "insert into openflow(NODE,PORT,TXPKTCNT,TXBYTES,RXPKTCNT,RXBYTES,TIME) values(%s,%s,%s,%s,%s,%s,%s)"
    cur.execute(sqli,t)  
    cur.close()
    conn.commit()
    conn.close()

#clear the table
def delete_data():
    conn = MySQLdb.connect(host='127.0.0.1',user='root',db ='test')
    cur = conn.cursor()
    cur.execute("delete from openflow")
    cur.close()
    conn.commit()
    conn.close()
         
h = httplib2.Http(".cache")
h.add_credentials('admin', 'admin')

if __name__ == "__main__":
    delete_data()
    while True:
        get_node_stats()
        time.sleep(10)    #30s
        
