import httplib2
import json


baseUrl = 'http://192.168.56.1:8080/controller/nb/v2'
containerName = 'default/'

def post_dict(h,url):
    resp, content = h.request(url,'PUT',headers = {'Content-Type' : 'application/xml'})
    return resp,content
  
def get_bandwidth(h,width1,width2):
    url1 = baseUrl + '/switchmanager/default/nodeconnector/OF/00:00:00:00:00:00:00:03/OF/1/property/bandwidth/' + str(width1)
    url2 = baseUrl + '/switchmanager/default/nodeconnector/OF/00:00:00:00:00:00:00:03/OF/2/property/bandwidth/' + str(width2)
    resp,content = post_dict(h,url1)
    resp,content = post_dict(h,url2)
 
def get_all_hosts_ip():
    all_hosts = get_all_hosts()
    host_list = []
    for host_prop in all_hosts:
        host_node_id = host_prop['networkAddress']
        host_list.append(host_node_id)
    return host_list
    
def get_all_hosts():
    host_list = get_all_wrapper('/hosttracker/default/hosts/active/', 'hostConfig')
    return host_list
    
def get_all_wrapper(typestring, attribute):
    url = baseUrl + typestring
    resp, content = h.request(url, "GET")
    allContent = json.loads(content)
    allrows = allContent[attribute]
    return allrows
    
h = httplib2.Http(".cache")
h.add_credentials('admin', 'admin')
if __name__ == "__main__":
    hostip = get_all_hosts_ip()
    print hostip
    ip1 = 0
    ip2 = 0
    for ip in hostip:
        if ip == u'10.0.0.1':
            ip1 = 1;
        elif ip == u'10.0.0.2':
            ip2 = 1;
    if(ip1 == 1 and ip2 == 0):
        get_bandwidth(h,100,10)
    elif(ip1 == 1 and ip2 == 1):
        get_bandwidth(h,50,50)
    else:
        print "Host1 and Host2 are not active!"
        
    
                
    

