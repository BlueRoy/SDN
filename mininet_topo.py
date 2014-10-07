from mininet.tpop import Topo

class MyTopo(Topo):
    def __init__(self):
        Topo.__init__(self)
        Host1 = self.addHost('h1')
        Host1 = self.addHost('h2')
        Host1 = self.addHost('h3')
        Host1 = self.addHost('h4')
        Switch1 = self.addSwitch('s1')
        Switch1 = self.addSwitch('s2')
        Switch1 = self.addSwitch('s3')
        
        self.addLink(Host1,Switch2)
        self.addLink(Host2,Switch1)
        self.addLink(Host3,Switch3)
        self.addLink(Host4,Switch3)
        self.addLink(Switch1,Switch2)
        self.addLink(Switch1,Switch3)
topos={'mytopo' : (lambda : MyTopo())}