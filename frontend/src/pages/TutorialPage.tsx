import { useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import { Button, MascotByte, type MascotMood } from '../components/ui';
import { Tooltip } from '../components/ui';
import { RappTooltipContent, type RappTemplate } from '../components/game/RappCatalogue';
import {
  ChevronLeft,
  ChevronRight,
  Home,
  Zap,
  Maximize,
  Shield,
  FileCheck,
  Settings,
  GitBranch,
  BellOff,
  AlertTriangle,
  Trophy,
  Sliders,
  Power,
  RotateCcw,
  Wifi,
  Activity,
  DollarSign,
  Users,
} from 'lucide-react';

// ---------------------------------------------------------------------------
// Step definitions
// ---------------------------------------------------------------------------
const STEPS: { title: string; mood: MascotMood; speech: string }[] = [
  {
    title: 'Welcome to rApp Tycoon!',
    mood: 'waving',
    speech:
      "Hi there, future network tycoon! I'm Byte, your AI network guide. I'll walk you through everything you need to dominate the 5G network. Let's go!",
  },
  {
    title: 'Your Mission',
    mood: 'excited',
    speech:
      "You're a network operator competing against up to 5 other players. Deploy automation apps, respond to incidents, and score the highest composite score before time runs out!",
  },
  {
    title: 'Your Basestations',
    mood: 'happy',
    speech:
      "You control 4 basestations — the towers that power the network. Each station has 6 key health metrics. Keep them in the green to keep your customers happy and your score high!",
  },
  {
    title: 'The rApp Catalogue',
    mood: 'excited',
    speech:
      "rApps are automation applications that supercharge your stations! Drag them from the catalogue on the left and drop onto a basestation to deploy. Each specialises in something different!",
  },
  {
    title: 'Managing Your rApps',
    mood: 'thinking',
    speech:
      "Once an rApp is running, you can tune its aggressiveness, disable it, or roll it back to a previous version. Keep an eye on your deployed apps — sometimes less is more!",
  },
  {
    title: 'Network Events',
    mood: 'thinking',
    speech:
      "Incidents will strike your basestations at random! Power outages, traffic spikes, hardware failures... They escalate over time if ignored. Deploy the right rApps fast to counter them!",
  },
  {
    title: 'How Scoring Works',
    mood: 'happy',
    speech:
      "Your composite score blends three things: how much money you have left, how happy your customers are, and how stable your network is. Balance all three to win!",
  },
  {
    title: "You're Ready to Play!",
    mood: 'celebrating',
    speech:
      "That's everything! You've got the knowledge to build the best network. Now get out there, crush those events, and climb to the top of the leaderboard. Good luck!",
  },
];

// ---------------------------------------------------------------------------
// Step content components
// ---------------------------------------------------------------------------
function WelcomeContent() {
  return (
    <div className="flex flex-col gap-4">
      <div className="text-center">
        <h3 className="text-3xl font-bold text-primary drop-shadow-[0_0_16px_rgba(6,182,212,0.4)]">
          rApp Tycoon
        </h3>
        <p className="text-text-muted text-sm mt-1">Deploy. Optimise. Dominate the network.</p>
      </div>
      <div className="grid grid-cols-3 gap-2">
        {[
          { icon: <Wifi size={18} className="text-primary" />, label: '5G Network Management' },
          { icon: <Trophy size={18} className="text-warning" />, label: 'Competitive Multiplayer' },
          { icon: <Zap size={18} className="text-accent" />, label: 'Real-time Strategy' },
        ].map(({ icon, label }) => (
          <div key={label} className="flex flex-col items-center gap-2 p-3 rounded-xl border border-surface-lighter bg-surface text-center">
            {icon}
            <span className="text-[11px] text-text-muted leading-tight">{label}</span>
          </div>
        ))}
      </div>
      <p className="text-sm text-text-muted leading-relaxed">
        Compete against up to 5 other players to manage a 5G network region for 5 minutes. Deploy the right apps, respond to incidents, and outscore the competition.
      </p>
    </div>
  );
}

function MissionContent() {
  return (
    <div className="flex flex-col gap-3">
      {[
        {
          icon: <Settings size={16} />,
          color: 'text-primary',
          bg: 'bg-primary/10 border-primary/20',
          title: 'Deploy rApps',
          desc: 'Place automation apps on your basestations to boost network metrics',
        },
        {
          icon: <AlertTriangle size={16} />,
          color: 'text-warning',
          bg: 'bg-warning/10 border-warning/20',
          title: 'Respond to Events',
          desc: 'Counter incidents before they escalate and damage your score',
        },
        {
          icon: <Trophy size={16} />,
          color: 'text-accent',
          bg: 'bg-accent/10 border-accent/20',
          title: 'Score the Highest',
          desc: 'The player with the best composite score after 60 ticks wins!',
        },
      ].map(({ icon, color, bg, title, desc }) => (
        <div key={title} className={`flex items-start gap-3 p-3 rounded-xl border ${bg}`}>
          <div className={`mt-0.5 shrink-0 ${color}`}>{icon}</div>
          <div>
            <p className={`text-sm font-semibold ${color}`}>{title}</p>
            <p className="text-xs text-text-muted mt-0.5">{desc}</p>
          </div>
        </div>
      ))}
      <div className="rounded-xl border border-surface-lighter bg-surface px-4 py-2.5">
        <p className="text-xs text-text-muted text-center">
          ⏱ <span className="text-text font-medium">60 ticks (~5 min)</span> &nbsp;·&nbsp; 👥 <span className="text-text font-medium">2–6 players</span> &nbsp;·&nbsp; 🤖 Bots supported
        </p>
      </div>
    </div>
  );
}

function BasestationsContent() {
  const metrics = [
    { label: 'Health', a: 85, b: 72, c: 91, color: 'bg-accent' },
    { label: 'Customer Exp.', a: 78, b: 65, c: 88, color: 'bg-primary' },
    { label: 'Energy Eff.', a: 70, b: 83, c: 62, color: 'bg-warning' },
  ];

  return (
    <div className="flex flex-col gap-3">
      <div className="grid grid-cols-3 gap-2">
        {(['Alpha', 'Beta', 'Gamma'] as const).map((name, i) => (
          <div key={name} className="rounded-xl border border-surface-lighter bg-surface p-2.5">
            <div className="flex justify-center mb-2">
              <div className="w-8 h-8 rounded-full bg-primary/15 border border-primary/30 flex items-center justify-center">
                <Wifi size={14} className="text-primary" />
              </div>
            </div>
            <p className="text-[11px] font-semibold text-text text-center mb-2">{name}</p>
            <div className="flex flex-col gap-1.5">
              {metrics.map(({ label, a, b, c, color }) => {
                const val = [a, b, c][i];
                return (
                  <div key={label}>
                    <div className="flex justify-between text-[9px] text-text-muted mb-0.5">
                      <span>{label}</span>
                      <span>{val}%</span>
                    </div>
                    <div className="h-1 rounded-full bg-surface-lighter">
                      <div className={`h-1 rounded-full ${color} transition-all`} style={{ width: `${val}%` }} />
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        ))}
      </div>
      <div className="rounded-xl border border-surface-lighter bg-surface px-3 py-2">
        <p className="text-xs text-text-muted leading-relaxed">
          <span className="text-text font-medium">6 metrics per station:</span> Health, Customer Experience, Cost, Energy Efficiency, Automation Reliability, SLA Compliance.
        </p>
      </div>
    </div>
  );
}

const RAPP_ICONS: Record<string, React.ComponentType<{ size?: number; className?: string }>> = {
  'Energy Saver': Zap,
  'Capacity Optimiser': Maximize,
  'Fault Predictor': Shield,
  'SLA Guardian': FileCheck,
  'Configuration Drift Detector': Settings,
  'Traffic Balancer': GitBranch,
  'Alarm Noise Reducer': BellOff,
};

const RAPP_COLORS: Record<string, string> = {
  'Energy Saver': 'text-accent',
  'Capacity Optimiser': 'text-primary',
  'Fault Predictor': 'text-warning',
  'SLA Guardian': 'text-danger',
  'Configuration Drift Detector': 'text-primary',
  'Traffic Balancer': 'text-accent',
  'Alarm Noise Reducer': 'text-text-muted',
};

const noImpact = { health: 0, customerExperience: 0, cost: 0, energyEfficiency: 0, automationReliability: 0, slaCompliance: 0 };

const RAPP_CATALOGUE: RappTemplate[] = [
  {
    id: 1, name: 'Energy Saver', cost: 30, risk: 15, confidence: 88,
    purpose: 'Reduces energy consumption by dynamically scaling down unused capacity and optimising power modes during low-traffic periods.',
    benefit: 'Lowers energy costs and improves energy efficiency',
    sideEffects: 'May slightly reduce throughput during aggressive power-saving cycles.',
    impact: { ...noImpact, energyEfficiency: 12, cost: -8 },
  },
  {
    id: 2, name: 'Capacity Optimiser', cost: 50, risk: 25, confidence: 82,
    purpose: 'Dynamically allocates additional radio resources to high-demand cells, boosting throughput and reducing congestion.',
    benefit: 'Improves customer experience and reduces traffic congestion',
    sideEffects: 'Increases energy consumption as a side effect.',
    impact: { ...noImpact, customerExperience: 15, energyEfficiency: -6 },
  },
  {
    id: 3, name: 'Fault Predictor', cost: 45, risk: 10, confidence: 79,
    purpose: 'Uses ML-based anomaly detection to identify early warning signs of hardware failures before they cause outages.',
    benefit: 'Prevents hardware failures and improves automation reliability',
    sideEffects: '',
    impact: { ...noImpact, health: 8, automationReliability: 10 },
  },
  {
    id: 4, name: 'SLA Guardian', cost: 55, risk: 20, confidence: 85,
    purpose: 'Monitors and enforces service level agreements in real time, rerouting traffic and adjusting parameters to maintain compliance.',
    benefit: 'Maintains SLA compliance and customer experience during incidents',
    sideEffects: 'May introduce minor latency overhead due to constant SLA monitoring.',
    impact: { ...noImpact, slaCompliance: 18, customerExperience: 5 },
  },
  {
    id: 5, name: 'Configuration Drift Detector', cost: 40, risk: 8, confidence: 91,
    purpose: 'Continuously monitors rApp configurations and network settings for unintended changes, triggering alerts or auto-corrections.',
    benefit: 'Prevents configuration conflicts and maintains automation reliability',
    sideEffects: '',
    impact: { ...noImpact, automationReliability: 12 },
  },
  {
    id: 6, name: 'Traffic Balancer', cost: 60, risk: 30, confidence: 76,
    purpose: 'Redistributes network load across underutilised basestations to prevent single-cell overload and improve overall stability.',
    benefit: 'Reduces congestion and improves health during traffic spikes',
    sideEffects: 'Increased handover frequency may temporarily affect customer experience.',
    impact: { ...noImpact, health: 10, customerExperience: -4 },
  },
  {
    id: 7, name: 'Alarm Noise Reducer', cost: 35, risk: 12, confidence: 84,
    purpose: 'Applies ML filtering to suppress false-positive alerts, letting you focus on genuine incidents that require action.',
    benefit: 'Clears alert noise and improves automation reliability',
    sideEffects: 'Small risk of suppressing genuinely important low-severity alerts.',
    impact: { ...noImpact, automationReliability: 8 },
  },
];

function RappCatalogueContent() {
  return (
    <div className="flex flex-col gap-3">
      <div className="grid grid-cols-2 gap-2">
        {RAPP_CATALOGUE.map((rapp) => {
          const Icon = RAPP_ICONS[rapp.name] ?? Settings;
          const color = RAPP_COLORS[rapp.name] ?? 'text-text-muted';
          return (
            <Tooltip key={rapp.id} content={<RappTooltipContent rapp={rapp} />} side="top">
              <div className="flex items-center gap-2.5 p-2.5 rounded-xl border border-surface-lighter bg-surface cursor-default hover:border-primary/30 transition-colors">
                <div className={`shrink-0 ${color}`}>
                  <Icon size={16} />
                </div>
                <div className="min-w-0">
                  <p className="text-xs font-medium text-text truncate">{rapp.name}</p>
                  <p className="text-[10px] text-text-muted">€{rapp.cost}</p>
                </div>
              </div>
            </Tooltip>
          );
        })}
      </div>
      <div className="rounded-xl border border-primary/20 bg-primary/5 px-3 py-2">
        <p className="text-xs text-text-muted leading-relaxed">
          <span className="text-primary font-medium">7 rApp types</span> available. Hover each card to learn more. In-game, drag from the left catalogue onto any basestation.
        </p>
      </div>
    </div>
  );
}

function ManageRappsContent() {
  return (
    <div className="flex flex-col gap-2.5">
      {[
        {
          Icon: Sliders,
          action: 'Tune',
          color: 'text-primary',
          bg: 'bg-primary/10 border-primary/20',
          desc: "Adjust threshold (1–100) and aggressiveness (LOW / MODERATE / HIGH) to fine-tune an rApp's impact on metrics.",
        },
        {
          Icon: Power,
          action: 'Disable',
          color: 'text-warning',
          bg: 'bg-warning/10 border-warning/20',
          desc: "Instantly pause an rApp's effects. Useful if it's causing unintended side effects on a metric.",
        },
        {
          Icon: RotateCcw,
          action: 'Rollback',
          color: 'text-danger',
          bg: 'bg-danger/10 border-danger/20',
          desc: 'Revert an rApp to its previous configuration if a tune went wrong.',
        },
      ].map(({ Icon, action, color, bg, desc }) => (
        <div key={action} className={`flex items-start gap-3 p-3 rounded-xl border ${bg}`}>
          <Icon size={17} className={`mt-0.5 shrink-0 ${color}`} />
          <div>
            <p className={`text-sm font-semibold ${color}`}>{action}</p>
            <p className="text-xs text-text-muted mt-0.5 leading-relaxed">{desc}</p>
          </div>
        </div>
      ))}
      <p className="text-xs text-text-muted text-center">
        Access these by clicking any basestation on the 3D map.
      </p>
    </div>
  );
}

const EVENT_TYPES = [
  { name: 'Power Outage', sev: 'HIGH', color: 'text-danger', bg: 'bg-danger/10 border-danger/25' },
  { name: 'Traffic Spike', sev: 'MED', color: 'text-warning', bg: 'bg-warning/10 border-warning/25' },
  { name: 'Hardware Failure', sev: 'HIGH', color: 'text-danger', bg: 'bg-danger/10 border-danger/25' },
  { name: 'SLA Breach', sev: 'CRIT', color: 'text-orange-400', bg: 'bg-orange-500/10 border-orange-500/25' },
  { name: 'Interference', sev: 'LOW', color: 'text-primary', bg: 'bg-primary/10 border-primary/25' },
  { name: 'Capacity Overflow', sev: 'MED', color: 'text-warning', bg: 'bg-warning/10 border-warning/25' },
];

function NetworkEventsContent() {
  return (
    <div className="flex flex-col gap-3">
      <div className="grid grid-cols-2 gap-2">
        {EVENT_TYPES.map(({ name, sev, color, bg }) => (
          <div key={name} className={`flex items-center gap-2 p-2.5 rounded-xl border ${bg}`}>
            <AlertTriangle size={13} className={color} />
            <div className="min-w-0">
              <p className={`text-[11px] font-medium ${color} truncate`}>{name}</p>
              <p className="text-[9px] text-text-muted">{sev}</p>
            </div>
          </div>
        ))}
      </div>
      <div className="rounded-xl border border-surface-lighter bg-surface p-3">
        <div className="flex items-center gap-3 mb-1.5">
          {[
            { label: 'LOW', dot: 'bg-blue-400' },
            { label: 'MED', dot: 'bg-warning' },
            { label: 'HIGH', dot: 'bg-orange-400' },
            { label: 'CRIT', dot: 'bg-danger' },
          ].map(({ label, dot }) => (
            <div key={label} className="flex items-center gap-1">
              <div className={`w-2 h-2 rounded-full ${dot}`} />
              <span className="text-[10px] text-text-muted">{label}</span>
            </div>
          ))}
        </div>
        <p className="text-[11px] text-text-muted leading-relaxed">
          Events escalate up to 3 times if ignored, then auto-resolve with permanent metric damage!
        </p>
      </div>
    </div>
  );
}

function ScoringContent() {
  return (
    <div className="flex flex-col gap-3">
      <div className="rounded-xl border border-surface-lighter bg-surface p-4">
        <p className="text-[11px] text-text-muted text-center mb-3 uppercase tracking-wide">Composite Score Formula</p>
        <div className="flex flex-wrap items-center justify-center gap-x-2 gap-y-1 text-sm font-mono">
          <span className="font-bold text-text">Score</span>
          <span className="text-text-muted">=</span>
          <span className="text-warning font-semibold">Money × 30%</span>
          <span className="text-text-muted">+</span>
          <span className="text-primary font-semibold">Satisfaction × 35%</span>
          <span className="text-text-muted">+</span>
          <span className="text-accent font-semibold">Stability × 35%</span>
        </div>
      </div>
      <div className="grid grid-cols-3 gap-2">
        {[
          { Icon: DollarSign, label: 'Money', pct: '30%', color: 'text-warning', bg: 'bg-warning/10 border-warning/20', desc: 'Starting €1,000 minus costs' },
          { Icon: Users, label: 'Satisfaction', pct: '35%', color: 'text-primary', bg: 'bg-primary/10 border-primary/20', desc: 'Avg customer experience' },
          { Icon: Activity, label: 'Stability', pct: '35%', color: 'text-accent', bg: 'bg-accent/10 border-accent/20', desc: 'Avg health & reliability' },
        ].map(({ Icon, label, pct, color, bg, desc }) => (
          <div key={label} className={`flex flex-col items-center gap-1.5 p-3 rounded-xl border ${bg}`}>
            <Icon size={16} className={color} />
            <p className={`text-[11px] font-semibold ${color}`}>{label}</p>
            <p className={`text-lg font-bold ${color}`}>{pct}</p>
            <p className="text-[10px] text-text-muted text-center leading-tight">{desc}</p>
          </div>
        ))}
      </div>
    </div>
  );
}

function CompleteContent() {
  return (
    <div className="flex flex-col items-center gap-4 text-center">
      <motion.div
        animate={{ scale: [1, 1.12, 1], rotate: [0, 6, -6, 0] }}
        transition={{ duration: 1.8, repeat: Infinity, repeatDelay: 0.8 }}
        className="text-5xl"
      >
        🏆
      </motion.div>
      <div>
        <h3 className="text-xl font-bold text-text">Tutorial Complete!</h3>
        <p className="text-sm text-text-muted mt-1">You're ready to become a network tycoon.</p>
      </div>
      <div className="grid grid-cols-2 gap-2 w-full">
        {[
          { emoji: '📡', text: 'Deploy rApps to boost your metrics' },
          { emoji: '⚡', text: 'Respond fast to network events' },
          { emoji: '🎯', text: 'Balance money, satisfaction & stability' },
          { emoji: '🥇', text: 'Climb to the top of the leaderboard' },
        ].map(({ emoji, text }) => (
          <div key={text} className="flex items-center gap-2 p-2.5 rounded-xl border border-surface-lighter bg-surface text-left">
            <span className="text-base shrink-0">{emoji}</span>
            <span className="text-xs text-text-muted leading-tight">{text}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function StepContent({ stepIndex }: { stepIndex: number }) {
  switch (stepIndex) {
    case 0: return <WelcomeContent />;
    case 1: return <MissionContent />;
    case 2: return <BasestationsContent />;
    case 3: return <RappCatalogueContent />;
    case 4: return <ManageRappsContent />;
    case 5: return <NetworkEventsContent />;
    case 6: return <ScoringContent />;
    case 7: return <CompleteContent />;
    default: return null;
  }
}

// ---------------------------------------------------------------------------
// Main Tutorial Page
// ---------------------------------------------------------------------------
export function TutorialPage() {
  const [stepIndex, setStepIndex] = useState(0);
  const navigate = useNavigate();

  const totalSteps = STEPS.length;
  const currentStep = STEPS[stepIndex];
  const isLast = stepIndex === totalSteps - 1;

  const goNext = () => { if (!isLast) setStepIndex((i) => i + 1); };
  const goBack = () => { if (stepIndex > 0) setStepIndex((i) => i - 1); };
  const returnToLobby = () => navigate('/');

  return (
    <div className="relative flex h-full items-center justify-center p-4 bg-linear-to-br from-surface via-surface to-surface-light overflow-y-auto">
      <div className="w-full max-w-2xl py-4">

        {/* Top bar */}
        <div className="flex items-center justify-between mb-4">
          <button
            onClick={returnToLobby}
            className="flex items-center gap-1.5 text-sm text-text-muted hover:text-text transition-colors"
          >
            <Home size={14} />
            <span>Back to Lobby</span>
          </button>
          <span className="text-xs text-text-muted font-medium">
            {stepIndex + 1} / {totalSteps}
          </span>
        </div>

        {/* Progress dots */}
        <div className="flex justify-center items-center gap-1.5 mb-5">
          {STEPS.map((_, i) => (
            <button
              key={i}
              onClick={() => setStepIndex(i)}
              aria-label={`Go to step ${i + 1}`}
              className={`rounded-full transition-all duration-300 ${
                i === stepIndex
                  ? 'w-6 h-2.5 bg-primary'
                  : i < stepIndex
                  ? 'w-2.5 h-2.5 bg-primary/40 hover:bg-primary/60'
                  : 'w-2.5 h-2.5 bg-surface-lighter hover:bg-surface-light'
              }`}
            />
          ))}
        </div>

        {/* Main card */}
        <div className="rounded-2xl border border-surface-lighter bg-surface-light p-5 md:p-6">

          {/* Mascot + speech bubble */}
          <div className="flex items-start gap-4 mb-5">
            <MascotByte mood={currentStep.mood} />
            <AnimatePresence mode="wait">
              <motion.div
                key={`speech-${stepIndex}`}
                initial={{ opacity: 0, x: 8 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -8 }}
                transition={{ duration: 0.22 }}
                className="relative flex-1"
              >
                {/* Arrow pointing left toward mascot */}
                <div className="absolute -left-2 top-4 border-y-[7px] border-y-transparent border-r-8 border-r-surface" />
                <div className="rounded-2xl rounded-tl-sm bg-surface border border-surface-lighter px-4 py-3">
                  <p className="text-sm text-text leading-relaxed">{currentStep.speech}</p>
                </div>
              </motion.div>
            </AnimatePresence>
          </div>

          {/* Step title + content */}
          <AnimatePresence mode="wait">
            <motion.div
              key={`content-${stepIndex}`}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              transition={{ duration: 0.22 }}
            >
              <h2 className="text-lg font-bold text-text mb-4">{currentStep.title}</h2>
              <StepContent stepIndex={stepIndex} />
            </motion.div>
          </AnimatePresence>
        </div>

        {/* Navigation */}
        <div className="flex justify-between mt-4">
          <Button
            variant="ghost"
            size="md"
            onClick={goBack}
            disabled={stepIndex === 0}
            className="flex items-center gap-1.5"
          >
            <ChevronLeft size={16} />
            Back
          </Button>

          {isLast ? (
            <Button
              variant="primary"
              size="md"
              onClick={returnToLobby}
              className="flex items-center gap-1.5"
            >
              <Home size={16} />
              Return to Lobby
            </Button>
          ) : (
            <Button
              variant="primary"
              size="md"
              onClick={goNext}
              className="flex items-center gap-1.5"
            >
              Next
              <ChevronRight size={16} />
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}
